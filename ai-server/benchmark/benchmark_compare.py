import cv2
import time
import joblib
import json
import redis
import mediapipe as mp
import pandas as pd
import numpy as np
from collections import deque
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

import firebase_admin
from firebase_admin import credentials, firestore

import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), ".."))

from main import (
    build_row, step2_resolve_nan, step3_smoothing_savgol,
    step4_pose_normalize, step5_make_features, FEATURE_COLUMNS,
    calc_risk_score, classify_level,
    MODEL_PATH, SCALER_PATH, WINDOW_SIZE, STRIDE
)

# ============================================================
# ✏️ [수정 필요] Firebase 설정
# ============================================================
KEY_PATH = "on-safe-f1667-firebase-adminsdk-fbsvc-91d2831472.json"               # ✏️ 서비스 계정 키 파일명
PROJECT_ID = "on-safe-f1667"             # ✏️ Firebase 프로젝트 ID
COLLECTION_RESULT = "onsafe_ai_results"   # AI 결과만 저장하는 컬렉션
COLLECTION_ALL    = "onsafe_all_data"     # 피처 + AI 결과 모두 저장하는 컬렉션
# ============================================================


def step6_scale_local(df, scaler):
    X = df[FEATURE_COLUMNS].copy()
    X = X.replace([np.inf, -np.inf], 0.0).fillna(0.0)
    return scaler.transform(X.values)


def init_firestore():
    cred = credentials.Certificate(KEY_PATH)
    if not firebase_admin._apps:
        firebase_admin.initialize_app(cred, {"projectId": PROJECT_ID})
    return firestore.client()


def clear_collection(db, collection_name):
    for doc in db.collection(collection_name).stream():
        doc.reference.delete()


def run_pipeline(video_path):
    print(f"\n▶️  [{video_path}] 파이프라인 시작\n")

    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)

    # Redis 연결
    try:
        r = redis.Redis(host='localhost', port=6379, db=0, decode_responses=True)
        r.ping()
        redis_key = "onsafe:features"
        r.delete(redis_key)
        print("✅ Redis 연결 성공")
    except Exception as e:
        print(f"❌ Redis 연결 실패: {e}")
        return None

    # Firestore 연결
    try:
        db = init_firestore()
        clear_collection(db, COLLECTION_RESULT)
        clear_collection(db, COLLECTION_ALL)
        print("✅ Firestore 연결 성공\n")
    except Exception as e:
        print(f"❌ Firestore 연결 실패: {e}")
        return None

    # MediaPipe 초기화
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

    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0

    frame_buffer = deque(maxlen=WINDOW_SIZE)
    frame_idx = 0
    inference_count = 0

    time_stats = {
        "1. Video Read & MediaPipe":      0.0,
        "2. NaN 보간 (Step 2)":            0.0,
        "3. Smoothing (Step 3)":           0.0,
        "4. Norm & Features (Step 4~6)":   0.0,
        "5. XGBoost Inference":            0.0,
        "6. Redis  — 피처 저장":            0.0,
        "7. Firestore — AI결과만 저장":     0.0,
        "8. Firestore — 피처+AI결과 저장":  0.0,
    }

    print("⏳ 동영상 처리 중...")
    total_start = time.perf_counter()

    while cap.isOpened():
        t0 = time.perf_counter()
        ret, frame = cap.read()
        if not ret:
            break

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        results = pose.detect(mp_image)

        if not results.pose_landmarks:
            frame_idx += 1
            time_stats["1. Video Read & MediaPipe"] += time.perf_counter() - t0
            continue

        landmarks = [
            {"x": lm.x, "y": lm.y, "z": lm.z, "v": lm.visibility}
            for lm in results.pose_landmarks[0]
        ]
        msg = {"frame": frame_idx, "timestamp": frame_idx / fps, "landmarks": landmarks}
        row = build_row(msg)
        frame_buffer.append(row)
        time_stats["1. Video Read & MediaPipe"] += time.perf_counter() - t0

        if len(frame_buffer) == WINDOW_SIZE and (frame_idx % STRIDE == 0):
            df_win = pd.DataFrame(frame_buffer)

            t1 = time.perf_counter()
            df_win = step2_resolve_nan(df_win)
            time_stats["2. NaN 보간 (Step 2)"] += time.perf_counter() - t1

            t2 = time.perf_counter()
            df_win = step3_smoothing_savgol(df_win)
            time_stats["3. Smoothing (Step 3)"] += time.perf_counter() - t2

            t3 = time.perf_counter()
            df_win = step4_pose_normalize(df_win)
            df_win = step5_make_features(df_win)
            X = step6_scale_local(df_win, scaler)
            time_stats["4. Norm & Features (Step 4~6)"] += time.perf_counter() - t3

            # 피처 벡터 (마지막 행 기준, 47개 값)
            feature_dict = {
                col: float(df_win[col].iloc[-1])
                for col in FEATURE_COLUMNS
            }

            t4 = time.perf_counter()
            proba = model.predict_proba(X)
            score = calc_risk_score(proba)
            level = classify_level(score)
            time_stats["5. XGBoost Inference"] += time.perf_counter() - t4

            ts_now = time.time()

            # -----------------------------------------------
            # [구간 6] Redis — AI 넣기 전 피처만 저장
            # -----------------------------------------------
            t5 = time.perf_counter()
            redis_payload = {"frame": frame_idx, "timestamp": ts_now}
            redis_payload.update(feature_dict)
            r.lpush(redis_key, json.dumps(redis_payload))
            time_stats["6. Redis  — 피처 저장"] += time.perf_counter() - t5

            # -----------------------------------------------
            # [구간 7] Firestore — AI 결과만 저장
            # -----------------------------------------------
            t6 = time.perf_counter()
            result_only = {
                "frame": frame_idx,
                "score": score,
                "level": level,
                "timestamp": ts_now,
            }
            db.collection(COLLECTION_RESULT).document(f"frame_{frame_idx}").set(result_only)
            time_stats["7. Firestore — AI결과만 저장"] += time.perf_counter() - t6

            # -----------------------------------------------
            # [구간 8] Firestore — 피처 + AI 결과 모두 저장
            # -----------------------------------------------
            t7 = time.perf_counter()
            all_data = {"frame": frame_idx, "score": score, "level": level, "timestamp": ts_now}
            all_data.update(feature_dict)
            db.collection(COLLECTION_ALL).document(f"frame_{frame_idx}").set(all_data)
            time_stats["8. Firestore — 피처+AI결과 저장"] += time.perf_counter() - t7

            inference_count += 1

        frame_idx += 1

    total_elapsed = time.perf_counter() - total_start
    cap.release()
    pose.close()

    print("\n" + "=" * 60)
    print("  📊 파이프라인 구간별 시간 프로파일링 결과")
    print("=" * 60)
    print(f"  🎬 총 프레임 수   : {frame_idx}")
    print(f"  🚀 총 추론 횟수   : {inference_count}")
    print(f"  ⏱️  전체 소요 시간 : {total_elapsed:.2f} 초")
    print("-" * 60)
    for name, t in time_stats.items():
        pct = (t / total_elapsed) * 100
        bar = "█" * int(pct / 2)
        print(f"  {name:<38} {t:6.3f}s  ({pct:5.1f}%) {bar}")
    print("=" * 60)

    return r, redis_key, db, inference_count


def benchmark_read_and_avg(r, redis_key, db, inference_count):
    if inference_count < 3:
        print("\n⚠️  추론 횟수가 3회 미만이라 평균 비교를 건너뜁니다.")
        return

    REPEAT = 100

    # -----------------------------------------------
    # 케이스 A: Redis 피처 조회 → Firestore AI결과 조회 → 평균
    # -----------------------------------------------
    case_a_times = []
    for _ in range(REPEAT):
        t0 = time.perf_counter()

        # Redis에서 최근 3건 피처 읽기 — O(1)
        raw = r.lrange(redis_key, 0, 2)
        _ = [json.loads(x) for x in raw]

        # Firestore에서 최근 3건 AI 결과 읽기 — O(log N)
        docs = (
            db.collection(COLLECTION_RESULT)
            .order_by("timestamp", direction=firestore.Query.DESCENDING)
            .limit(3)
            .stream()
        )
        scores = [doc.to_dict()["score"] for doc in docs]
        avg_a = sum(scores) / len(scores)

        case_a_times.append(time.perf_counter() - t0)

    # -----------------------------------------------
    # 케이스 B: Firestore 피처+AI결과 모두 조회 → 평균
    # -----------------------------------------------
    case_b_times = []
    for _ in range(REPEAT):
        t0 = time.perf_counter()

        docs = (
            db.collection(COLLECTION_ALL)
            .order_by("timestamp", direction=firestore.Query.DESCENDING)
            .limit(3)
            .stream()
        )
        scores = [doc.to_dict()["score"] for doc in docs]
        avg_b = sum(scores) / len(scores)

        case_b_times.append(time.perf_counter() - t0)

    # -----------------------------------------------
    # 결과 출력
    # -----------------------------------------------
    def stats(times):
        return sum(times)/len(times), min(times), max(times)

    a_avg, a_min, a_max = stats(case_a_times)
    b_avg, b_min, b_max = stats(case_b_times)

    print("\n" + "=" * 60)
    print("  📊 최근 3건 조회 + score 평균 — 케이스 비교")
    print(f"  (각 {REPEAT}회 반복 측정)")
    print("=" * 60)

    print("""
  [케이스 A] Redis(피처) + Firestore(AI결과만)
    └─ 피처  : Redis LRANGE  → O(1), 인메모리
    └─ 결과  : Firestore 쿼리 → O(log N), 네트워크 왕복
    """)
    print(f"     평균 : {a_avg*1000:.3f} ms")
    print(f"     최소 : {a_min*1000:.3f} ms")
    print(f"     최대 : {a_max*1000:.3f} ms")

    print("""
  [케이스 B] Firestore(피처 + AI결과 전부)
    └─ 피처+결과 : Firestore 쿼리 → O(log N), 네트워크 왕복
                   문서 크기 大 (47개 피처 + AI결과)
    """)
    print(f"     평균 : {b_avg*1000:.3f} ms")
    print(f"     최소 : {b_min*1000:.3f} ms")
    print(f"     최대 : {b_max*1000:.3f} ms")

    ratio = b_avg / a_avg if a_avg > 0 else float("inf")
    faster = "케이스 A" if a_avg < b_avg else "케이스 B"
    print(f"\n  ✅ {faster}가 약 {ratio:.1f}배 빠름")

    print("\n" + "=" * 60)
    print("  📖 시간 복잡도 정리")
    print("=" * 60)
    print("""
  ┌──────────┬────────────────┬──────────────────────────────┐
  │          │  쓰기           │  읽기 (최근 3건 + 평균)      │
  ├──────────┼────────────────┼──────────────────────────────┤
  │ 케이스 A │ Redis  O(1)    │ Redis  O(1)  ← 피처          │
  │          │ FS     O(logN) │ FS     O(logN) ← AI 결과     │
  │          │ (2번 쓰기)     │ (2번 읽기, 네트워크 1회)      │
  ├──────────┼────────────────┼──────────────────────────────┤
  │ 케이스 B │ FS     O(logN) │ FS     O(logN)               │
  │          │ (1번 쓰기)     │ 문서 크기 大 → 전송량 多      │
  │          │ 문서 크기 大   │ (네트워크 1회)                │
  └──────────┴────────────────┴──────────────────────────────┘

  핵심 차이:
  - 케이스 A: 피처는 Redis(인메모리)라 빠르지만 두 곳을 따로 조회
  - 케이스 B: 한 곳에서 조회하지만 문서가 커서 네트워크 전송량 증가
  - 실시간 추론 서버엔 A가 유리, 장기 분석/로그엔 B가 유리
    """)
    print("=" * 60)


if __name__ == "__main__":
    # ✏️ [수정 필요] 테스트할 영상 파일명
    result = run_pipeline("fall_01.mp4")

    if result:
        r, redis_key, db, inference_count = result
        benchmark_read_and_avg(r, redis_key, db, inference_count)