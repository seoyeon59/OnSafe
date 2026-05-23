import os
import time
import asyncio
import logging
import joblib
import numpy as np
import pandas as pd
import httpx
from collections import deque
from typing import Optional

from scipy.signal import savgol_filter
from fastapi import FastAPI, WebSocket, WebSocketDisconnect

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("onsafe")

app = FastAPI(
    title="OnSafe 실시간 낙상 추론 서버",
    description="안드로이드 앱이 보낸 MediaPipe landmark stream 을 받아 실시간 낙상 위험을 추론"
)

# =========================================================
# 0. 경로/환경 상수
# =========================================================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PKL_DIR = os.path.join(BASE_DIR, "pkl")
MODEL_PATH = os.path.join(PKL_DIR, "xgb_model.pkl")
SCALER_PATH = os.path.join(PKL_DIR, "scaler.pkl")
SPRING_EVENT_URL = os.environ.get(
    "SPRING_EVENT_URL", "http://backend:8080/api/internal/fall-event"
)

model = None
scaler = None
http_client: Optional[httpx.AsyncClient] = None


# =========================================================
# 1. 학습과 1:1 동일한 관절 트리플 / feature 순서
# =========================================================
JOINT_TRIPLETS = [
    ('neck',             0, 11, 12),
    ('shoulder_balance', 11, 0, 12),
    ('shoulder_left',    23, 11, 13),
    ('shoulder_right',   24, 12, 14),
    ('elbow_left',       11, 13, 15),
    ('elbow_right',      12, 14, 16),
    ('hip_left',         11, 23, 25),
    ('hip_right',        12, 24, 26),
    ('knee_left',        23, 25, 27),
    ('knee_right',       24, 26, 28),
    ('ankle_left',       25, 27, 31),
    ('ankle_right',      26, 28, 32),
    ('torso_left',       0, 11, 23),
    ('torso_right',      0, 12, 24),
    ('spine',            0, 23, 24),
]
JOINTS_ORDER = [
    'neck', 'shoulder_balance',
    'shoulder_left', 'shoulder_right',
    'elbow_left', 'elbow_right',
    'hip_left', 'hip_right',
    'knee_left', 'knee_right',
    'torso_left', 'torso_right', 'spine',
    'ankle_left', 'ankle_right',
]
FEATURE_COLUMNS: list[str] = []
for j in JOINTS_ORDER:
    FEATURE_COLUMNS += [
        f'{j}_angle',
        f'{j}_angular_velocity',
        f'{j}_angular_acceleration',
    ]
FEATURE_COLUMNS += ['center_distance', 'center_speed']
assert len(FEATURE_COLUMNS) == 47, f"Feature 개수 불일치: {len(FEATURE_COLUMNS)}"


# =========================================================
# 2. 실시간 추론 파라미터
# =========================================================
WINDOW_SIZE = 30          # 한 번에 추론할 프레임 수 (입력 30fps 가정 시 1초)
STRIDE = 5                # 추론 호출 간격 (프레임)
COOLDOWN_SEC = 10.0       # 같은 사고 중복 알림 방지
WARNING_THRESHOLD = 40.0  # 주의 (사업계획서 4.2)
CRITICAL_THRESHOLD = 70.0 # 경고
# TODO: 검증셋(5-fold CV)으로 임계값/WINDOW_SIZE 튜닝


# =========================================================
# 3. 모델/스케일러/HTTP 클라이언트 로드
# =========================================================
@app.on_event("startup")
def startup():
    global model, scaler, http_client
    if not os.path.exists(MODEL_PATH) or not os.path.exists(SCALER_PATH):
        raise RuntimeError(f"모델/스케일러 파일 없음: {MODEL_PATH}, {SCALER_PATH}")
    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)
    http_client = httpx.AsyncClient(timeout=3.0)
    logger.info("모델/스케일러 로드 완료")


@app.on_event("shutdown")
async def shutdown():
    global http_client
    if http_client is not None:
        await http_client.aclose()


# =========================================================
# 4. 폰이 보낸 JSON → wide-row dict 변환
# =========================================================
def build_row(msg: dict) -> dict:
    lms = msg["landmarks"]
    if len(lms) != 33:
        raise ValueError(f"landmark 33개 필요, 실제 {len(lms)}개")
    row = {"frame": msg["frame"], "timestamp": msg["timestamp"]}
    for i, lm in enumerate(lms):
        row[f"kp{i}_x"] = lm["x"]
        row[f"kp{i}_y"] = lm["y"]
        row[f"kp{i}_z"] = lm["z"]
        row[f"kp{i}_visibility"] = lm["v"]
    return row


# =========================================================
# 5. 전처리 파이프라인 — 학습 노트북 1:1 동일 (윈도우 단위)
# =========================================================
def step2_resolve_nan(df: pd.DataFrame, conf_threshold: float = 0.3) -> pd.DataFrame:
    df = df.copy()
    kp_x = sorted([c for c in df.columns if c.endswith('_x')])
    kp_y = sorted([c for c in df.columns if c.endswith('_y')])
    kp_z = sorted([c for c in df.columns if c.endswith('_z')])
    confs = sorted([c for c in df.columns if c.endswith('_visibility')])

    n_frames, n_joints = len(df), len(kp_x)
    kp = np.zeros((n_frames, n_joints, 3))
    conf = np.zeros((n_frames, n_joints))
    for j in range(n_joints):
        kp[:, j, 0] = df[kp_x[j]]
        kp[:, j, 1] = df[kp_y[j]]
        kp[:, j, 2] = df[kp_z[j]]
        conf[:, j] = df[confs[j]]

    kp[conf < conf_threshold] = np.nan
    mean = np.nanmean(kp, axis=(0, 1))
    std = np.nanstd(kp, axis=(0, 1))
    outlier = (kp < mean - 3 * std) | (kp > mean + 3 * std)
    kp[outlier] = np.nan

    for f in range(n_frames):
        for j in range(n_joints):
            if np.isnan(kp[f, j, 0]):
                prev_val, next_val = None, None
                for p in range(f - 1, -1, -1):
                    if not np.isnan(kp[p, j, 0]):
                        prev_val = kp[p, j, :]; break
                for q in range(f + 1, n_frames):
                    if not np.isnan(kp[q, j, 0]):
                        next_val = kp[q, j, :]; break
                if prev_val is not None and next_val is not None:
                    kp[f, j, :] = (prev_val + next_val) / 2
                elif prev_val is not None:
                    kp[f, j, :] = prev_val
                elif next_val is not None:
                    kp[f, j, :] = next_val

    for j in range(n_joints):
        df[kp_x[j]] = kp[:, j, 0]
        df[kp_y[j]] = kp[:, j, 1]
        df[kp_z[j]] = kp[:, j, 2]

    num_cols = df.select_dtypes(include='number').columns
    for c in num_cols:
        df[c] = df[c].interpolate(method='cubic', limit_direction='both').ffill().bfill()
    return df


def step3_smoothing_savgol(df: pd.DataFrame, window: int = 7, polyorder: int = 2) -> pd.DataFrame:
    df = df.copy()
    coord_cols = [c for c in df.columns if c.endswith(('_x', '_y', '_z'))]
    for col in coord_cols:
        arr = df[col].to_numpy()
        if len(arr) >= window:
            df[col] = savgol_filter(arr, window_length=window, polyorder=polyorder, mode='interp')
    return df


def step4_pose_normalize(df: pd.DataFrame, pelvis=(23, 24)) -> pd.DataFrame:
    df = df.copy()
    px = (df[f'kp{pelvis[0]}_x'] + df[f'kp{pelvis[1]}_x']) / 2
    py = (df[f'kp{pelvis[0]}_y'] + df[f'kp{pelvis[1]}_y']) / 2
    pz = (df[f'kp{pelvis[0]}_z'] + df[f'kp{pelvis[1]}_z']) / 2

    kp_x = [c for c in df.columns if c.endswith('_x')]
    kp_y = [c for c in df.columns if c.endswith('_y')]
    kp_z = [c for c in df.columns if c.endswith('_z')]
    for cx, cy, cz in zip(kp_x, kp_y, kp_z):
        df[cx] -= px
        df[cy] -= py
        df[cz] -= pz

    lx, ly, lz = df[f'kp{pelvis[0]}_x'], df[f'kp{pelvis[0]}_y'], df[f'kp{pelvis[0]}_z']
    rx, ry, rz = df[f'kp{pelvis[1]}_x'], df[f'kp{pelvis[1]}_y'], df[f'kp{pelvis[1]}_z']
    scale = np.sqrt((lx - rx) ** 2 + (ly - ry) ** 2 + (lz - rz) ** 2).replace(0, 1)
    for cx, cy, cz in zip(kp_x, kp_y, kp_z):
        df[cx] /= scale
        df[cy] /= scale
        df[cz] /= scale
    return df


def _compute_dt(timestamps: np.ndarray) -> np.ndarray:
    n = len(timestamps)
    dt = np.zeros_like(timestamps, dtype=float)
    if n >= 3:
        dt[1:-1] = (timestamps[2:] - timestamps[:-2]) / 2.0
    if n >= 2:
        dt[0] = timestamps[1] - timestamps[0]
        dt[-1] = timestamps[-1] - timestamps[-2]
    return np.where(dt == 0, 1e-6, dt)


def _calc_angle(a_idx, b_idx, c_idx, df: pd.DataFrame) -> np.ndarray:
    a = df[[f'kp{a_idx}_x', f'kp{a_idx}_y', f'kp{a_idx}_z']].values
    b = df[[f'kp{b_idx}_x', f'kp{b_idx}_y', f'kp{b_idx}_z']].values
    c = df[[f'kp{c_idx}_x', f'kp{c_idx}_y', f'kp{c_idx}_z']].values
    ba, bc = a - b, c - b
    dot = np.einsum('ij,ij->i', ba, bc)
    cross = np.linalg.norm(np.cross(ba, bc), axis=1)
    eps = 1e-6
    dot = np.where(np.abs(dot) < eps, eps, dot)
    cross = np.where(np.abs(cross) < eps, eps, cross)
    return np.degrees(np.arctan2(cross, dot))


def _central_diff(series: np.ndarray, dt: np.ndarray, to_radian: bool = False) -> np.ndarray:
    x = np.radians(series) if to_radian else series.astype(float)
    out = np.zeros_like(x)
    if len(x) > 2:
        out[1:-1] = (x[2:] - x[:-2]) / (2 * dt[1:-1])
    out = np.nan_to_num(out, nan=0.0, posinf=0.0, neginf=0.0)
    return np.degrees(out) if to_radian else out


def step5_make_features(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    dt = _compute_dt(df['timestamp'].values)

    for name, a, b, c in JOINT_TRIPLETS:
        angle = _calc_angle(a, b, c, df)
        omega = _central_diff(angle, dt, to_radian=True)
        alpha = _central_diff(omega, dt, to_radian=False)
        df[f'{name}_angle'] = angle
        df[f'{name}_angular_velocity'] = omega
        df[f'{name}_angular_acceleration'] = alpha

    coords = (
        df[['kp23_x', 'kp23_y', 'kp23_z']].values
        + df[['kp24_x', 'kp24_y', 'kp24_z']].values
    ) / 2
    diff = np.diff(coords, axis=0, prepend=coords[:1])
    df['center_distance'] = np.linalg.norm(diff, axis=1)

    ts = df['timestamp'].values
    dt_simple = np.diff(ts, prepend=ts[0])
    dt_simple = np.where(dt_simple == 0, 1e-6, dt_simple)
    df['center_speed'] = df['center_distance'] / dt_simple
    return df


def step6_scale(df: pd.DataFrame) -> np.ndarray:
    X = df[FEATURE_COLUMNS].copy()
    X = X.replace([np.inf, -np.inf], 0.0).fillna(0.0)
    return scaler.transform(X.values)


# =========================================================
# 6. 위험점수 산출 + 알림 단계 분기
# =========================================================
def calc_risk_score(probabilities: np.ndarray) -> float:
    fall_prob = probabilities[:, 1]
    return float(fall_prob.mean() * 100)


def classify_level(score: float) -> str:
    if score >= CRITICAL_THRESHOLD:
        return "CRITICAL"
    if score >= WARNING_THRESHOLD:
        return "WARNING"
    return "NORMAL"


# =========================================================
# 7. Spring Boot 이벤트 발행
# =========================================================
async def emit_event(device_id: str, score: float, level: str, frame_idx: int):
    if http_client is None:
        return
    payload = {
        "device_id": device_id,
        "fall_score": score,
        "level": level,
        "frame": frame_idx,
        "ts": time.time(),
    }
    try:
        await http_client.post(SPRING_EVENT_URL, json=payload)
    except Exception as e:
        logger.warning(f"백엔드 이벤트 발행 실패: {e}")


# =========================================================
# 8. /ws/predict — 실시간 추론 WebSocket
# =========================================================
@app.websocket("/ws/predict")
async def ws_predict(ws: WebSocket):
    if model is None or scaler is None:
        await ws.close(code=1011)
        return

    await ws.accept()

    try:
        init_msg = await ws.receive_json()
        if init_msg.get("type") != "init":
            await ws.close(code=4001)
            return
        device_id = init_msg["device_id"]
        # TODO: session_token 검증 (BE 팀과 협의)
    except Exception:
        await ws.close(code=4000)
        return

    logger.info(f"WS 연결: device={device_id}")
    frame_buffer: deque[dict] = deque(maxlen=WINDOW_SIZE)
    frame_count = 0
    last_alert_ts = 0.0

    try:
        while True:
            msg = await ws.receive_json()
            if msg.get("type") != "frame":
                continue

            try:
                row = build_row(msg)
            except (KeyError, ValueError) as e:
                logger.warning(f"잘못된 프레임 메시지: {e}")
                continue

            frame_buffer.append(row)
            frame_count += 1

            if len(frame_buffer) < WINDOW_SIZE:
                continue
            if frame_count % STRIDE != 0:
                continue

            try:
                df_win = pd.DataFrame(frame_buffer)
                df_win = step2_resolve_nan(df_win)
                df_win = step3_smoothing_savgol(df_win)
                df_win = step4_pose_normalize(df_win)
                df_win = step5_make_features(df_win)
                X = step6_scale(df_win)

                proba = model.predict_proba(X)
                score = calc_risk_score(proba)
                level = classify_level(score)
            except Exception as e:
                logger.exception(f"추론 에러: {e}")
                continue

            await ws.send_json({
                "type": "result",
                "frame": msg["frame"],
                "fall_score": score,
                "level": level,
                "ts": time.time(),
            })

            now = time.time()
            if level == "CRITICAL" and (now - last_alert_ts) > COOLDOWN_SEC:
                last_alert_ts = now
                asyncio.create_task(emit_event(device_id, score, level, msg["frame"]))

    except WebSocketDisconnect:
        logger.info(f"WS 연결 종료: device={device_id}")
    except Exception as e:
        logger.exception(f"WS 처리 에러: {e}")
        try:
            await ws.close(code=1011)
        except Exception:
            pass


# =========================================================
# 9. /health — 상태 체크
# =========================================================
@app.get("/health")
def health():
    return {
        "status": "ok" if (model is not None and scaler is not None) else "not_ready",
        "model_loaded": model is not None,
        "scaler_loaded": scaler is not None,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)