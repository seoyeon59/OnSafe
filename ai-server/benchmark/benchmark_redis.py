import cv2
import time
import joblib
import mediapipe as mp
import pandas as pd
import numpy as np
from collections import deque, defaultdict
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))

from main import (
    build_row, step2_resolve_nan, step3_smoothing_savgol,
    step4_pose_normalize, step5_make_features, FEATURE_COLUMNS,
    calc_risk_score, classify_level,
    MODEL_PATH, SCALER_PATH, WINDOW_SIZE, STRIDE
)


# -----------------------------------------------
# 이벤트 로거
# -----------------------------------------------
event_counts = defaultdict(int)
event_totals = defaultdict(float)
event_mins   = defaultdict(lambda: float("inf"))
event_maxs   = defaultdict(float)



def log_event(stage: str, elapsed: float, frame: int = None,
              score: float = None, level: str = None, extra: str = ""):
    event_counts[stage] += 1
    event_totals[stage] += elapsed
    event_mins[stage]    = min(event_mins[stage], elapsed)
    event_maxs[stage]    = max(event_maxs[stage], elapsed)

    n         = event_counts[stage]
    avg       = event_totals[stage] / n
    ts        = time.strftime("%H:%M:%S")
    frame_str = f"  frame={frame:5d}" if frame is not None else ""
    score_str = f"  score={score:6.2f}  level={level:<8}" if score is not None else ""
    extra_str = f"  {extra}" if extra else ""

    print(
        f"[EVENT] {ts}  "
        f"{stage:<45}"
        f"  elapsed={elapsed*1000:8.3f}ms"
        f"  avg={avg*1000:8.3f}ms"
        f"  (n={n:4d})"
        f"{frame_str}{score_str}{extra_str}"
    )


def step6_scale_local(df, scaler):
    X = df[FEATURE_COLUMNS].copy()
    X = X.replace([np.inf, -np.inf], 0.0).fillna(0.0)
    return scaler.transform(X.values)


def benchmark_avg(score_history: list):
    """최근 3개 score 평균 계산 시간 — 100회 반복 측정"""
    if len(score_history) < 3:
        print("\n⚠ 추론 횟수 3회 미만 — 평균 벤치마크 생략")
        return

    REPEAT = 100
    print(f"\n⏳ 최근 3개 평균 계산 벤치마크 ({REPEAT}회 반복)...\n")

    for _ in range(REPEAT):
        t0    = time.perf_counter()
        last3 = score_history[-3:]       # O(1) 리스트 슬라이싱
        avg   = sum(last3) / len(last3)  # O(1) 고정 크기 3
        log_event(
            "6. 최근 3개 score 평균  O(1)",
            time.perf_counter() - t0,
            extra=f"avg_score={avg:.2f}"
        )


def run_full_profiling(video_path):
    # 매 실행마다 초기화
    event_counts.clear()
    event_totals.clear()
    event_mins.clear()
    event_maxs.clear()

    print(f"▶️ [{video_path}] 전체 파이프라인 구간별 속도 테스트 시작\n")
    print("=" * 110)

    model  = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)

    # ✏️ [수정 필요] pose_landmarker_lite.task 경로
    base_options = mp_python.BaseOptions(model_asset_path="pose_landmarker_lite.task")
    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5
    )
    pose = vision.PoseLandmarker.create_from_options(options)

    cap             = cv2.VideoCapture(video_path)
    fps             = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frame_buffer    = deque(maxlen=WINDOW_SIZE)
    frame_idx       = 0
    inference_count = 0
    score_history   = []   # 추론 score 누적
    total_start     = time.perf_counter()

    print("⏳ 동영상 처리 중...\n")

    while cap.isOpened():

        # [구간 1] 영상 읽기 + MediaPipe — O(1)
        t0      = time.perf_counter()
        ret, frame = cap.read()
        if not ret:
            break

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image  = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        results   = pose.detect(mp_image)
        elapsed   = time.perf_counter() - t0

        if not results.pose_landmarks:
            log_event("1. Video Read & MediaPipe  O(1)",
                      elapsed, frame=frame_idx, extra="⚠ no_landmark")
            frame_idx += 1
            continue

        # 랜드마크 정상 감지
        log_event("1. Video Read & MediaPipe  O(1)", elapsed, frame=frame_idx)

        landmarks = [
            {"x": lm.x, "y": lm.y, "z": lm.z, "v": lm.visibility}
            for lm in results.pose_landmarks[0]
        ]
        msg = {"frame": frame_idx, "timestamp": frame_idx / fps, "landmarks": landmarks}
        row = build_row(msg)
        frame_buffer.append(row)

        if len(frame_buffer) == WINDOW_SIZE and (frame_idx % STRIDE == 0):
            df_win = pd.DataFrame(frame_buffer)

            # [구간 2] NaN 보간 — O(W*J)
            t1     = time.perf_counter()
            df_win = step2_resolve_nan(df_win)
            log_event(f"2. NaN 보간  O(W*J) W={WINDOW_SIZE},J=33",
                      time.perf_counter() - t1, frame=frame_idx)

            # [구간 3] Smoothing — O(W*F)
            t2     = time.perf_counter()
            df_win = step3_smoothing_savgol(df_win)
            log_event(f"3. Smoothing  O(W*F) F={len(FEATURE_COLUMNS)}",
                      time.perf_counter() - t2, frame=frame_idx)

            # [구간 4] 정규화 + 피처생성 + 스케일링 — O(W*F)
            t3     = time.perf_counter()
            df_win = step4_pose_normalize(df_win)
            df_win = step5_make_features(df_win)
            X      = step6_scale_local(df_win, scaler)
            log_event(f"4. Normalize+Features+Scale  O(W*F)",
                      time.perf_counter() - t3, frame=frame_idx)

            # [구간 5] XGBoost 추론 — O(W*T*D)
            t4           = time.perf_counter()
            proba        = model.predict_proba(X)
            score        = calc_risk_score(proba)
            level        = classify_level(score)
            n_estimators = getattr(model, "n_estimators", "?")
            max_depth    = getattr(model, "max_depth", "?")
            log_event(
                f"5. XGBoost  O(W*T*D) T={n_estimators},D={max_depth}",
                time.perf_counter() - t4,
                frame=frame_idx, score=score, level=level
            )

            score_history.append(score)
            inference_count += 1

        frame_idx += 1

    total_elapsed = time.perf_counter() - total_start
    cap.release()
    pose.close()
    # 최근 3개 평균 벤치마크
    benchmark_avg(score_history)

    print(f"\n[EVENT] 파이프라인 완료  총 프레임={frame_idx}  추론={inference_count}  전체={total_elapsed:.2f}s")

    # -----------------------------------------------
    # 최종 요약
    # -----------------------------------------------
    print("\n" + "=" * 110)
    print("  📊 구간별 이벤트 로그 최종 요약")
    print(f"  🎬 총 프레임={frame_idx}  추론={inference_count}  전체={total_elapsed:.2f}s")
    print("=" * 110)

    for stage in event_counts:
        n   = event_counts[stage]
        tot = event_totals[stage]
        avg = tot / n * 1000
        mn  = event_mins[stage] * 1000
        mx  = event_maxs[stage] * 1000
        print(f"  {stage}")
        print(f"    호출={n:4d}  누적={tot:8.3f}s  평균={avg:9.3f}ms  최소={mn:9.3f}ms  최대={mx:9.3f}ms")
        print()

    print("=" * 110)
    print("""
  📖 시간 복잡도 정리
  ──────────────────────────────────────────────────────────
  구간 1  Video Read & MediaPipe     O(1)         프레임 1장 처리, 상수 시간
  구간 2  NaN 보간                   O(W * J)     W=윈도우(30), J=관절수(33)
  구간 3  Savitzky-Golay Smoothing   O(W * F)     F=피처 컬럼 수(47)
  구간 4  Normalize + Features       O(W * F)     파생변수 계산 포함
  구간 5  XGBoost Inference          O(W * T * D) T=트리수, D=트리깊이
  구간 6  최근 3개 평균              O(1)         고정 크기 슬라이싱
  ──────────────────────────────────────────────────────────
  W, J, F, T, D 모두 학습 시 고정 상수 → 실질적으로 O(1) per 추론 호출
    """)


if __name__ == "__main__":
    # ✏️ [수정 필요] 테스트할 영상 파일명
    run_full_profiling("fall_01.mp4")