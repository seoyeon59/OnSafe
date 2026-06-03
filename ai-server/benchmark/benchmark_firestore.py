import cv2
import time
import joblib
import json
import mediapipe as mp
import pandas as pd
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

import numpy as np


def step6_scale_local(df, scaler):
    X = df[FEATURE_COLUMNS].copy()
    X = X.replace([np.inf, -np.inf], 0.0).fillna(0.0)
    return scaler.transform(X.values)


def init_firestore():
    # ✏️ [수정 필요] 발급받은 서비스 계정 키 JSON 파일 경로로 변경
    KEY_PATH = "on-safe-f1667-firebase-adminsdk-fbsvc-91d2831472.json"

    # ✏️ [수정 필요] Firebase 콘솔에서 확인한 프로젝트 ID로 변경
    PROJECT_ID = "on-safe-f1667"

    cred = credentials.Certificate(KEY_PATH)
    firebase_admin.initialize_app(cred, {
        "projectId": PROJECT_ID,
    })
    return firestore.client()


def run_full_profiling(video_path):
    print(f"▶️ [{video_path}] 전체 파이프라인 구간별 속도 테스트 시작\n")

    # 1. 모델/스케일러 로드
    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)

    # 2. Firestore 연결
    try:
        db = init_firestore()

        # ✏️ [수정 필요] Firestore에 저장할 컬렉션 이름 (자유롭게 지정)
        COLLECTION_NAME = "onsafe_logs"

        # 테스트용 기존 데이터 삭제 (선택사항 — 매번 초기화하려면 유지)
        docs = db.collection(COLLECTION_NAME).stream()
        for doc in docs:
            doc.reference.delete()

        print("✅ Firestore 연결 성공\n")
    except Exception as e:
        print(f"❌ Firestore 연결 실패: {e}")
        return

    # 3. MediaPipe 초기화
    # ✏️ [수정 필요] pose_landmarker_lite.task 파일 경로 (현재 폴더에 있으면 그대로)
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
        "1. Video Read & MediaPipe": 0.0,
        "2. Data Prep & NaN (Step 2)": 0.0,
        "3. Smoothing (Step 3)": 0.0,
        "4. Norm & Features (Step 4~6)": 0.0,
        "5. XGBoost Inference": 0.0,
        "6. Firestore Insert": 0.0
    }

    print("⏳ 동영상 처리 중... (각 구간별 시간 측정 중)")
    total_start_time = time.perf_counter()

    while cap.isOpened():
        # [구간 1] 영상 읽기 및 MediaPipe 랜드마크 추출
        t0 = time.perf_counter()
        ret, frame = cap.read()
        if not ret:
            break

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        results = pose.detect(mp_image)

        if not results.pose_landmarks:
            frame_idx += 1
            time_stats["1. Video Read & MediaPipe"] += (time.perf_counter() - t0)
            continue

        landmarks = [
            {"x": lm.x, "y": lm.y, "z": lm.z, "v": lm.visibility}
            for lm in results.pose_landmarks[0]
        ]
        msg = {"frame": frame_idx, "timestamp": frame_idx / fps, "landmarks": landmarks}
        row = build_row(msg)
        frame_buffer.append(row)
        time_stats["1. Video Read & MediaPipe"] += (time.perf_counter() - t0)

        if len(frame_buffer) == WINDOW_SIZE and (frame_idx % STRIDE == 0):
            df_win = pd.DataFrame(frame_buffer)

            # [구간 2] 결측치 보간
            t1 = time.perf_counter()
            df_win = step2_resolve_nan(df_win)
            time_stats["2. Data Prep & NaN (Step 2)"] += (time.perf_counter() - t1)

            # [구간 3] Savitzky-Golay 필터링
            t2 = time.perf_counter()
            df_win = step3_smoothing_savgol(df_win)
            time_stats["3. Smoothing (Step 3)"] += (time.perf_counter() - t2)

            # [구간 4] 정규화 + 피처 생성 + 스케일링
            t3 = time.perf_counter()
            df_win = step4_pose_normalize(df_win)
            df_win = step5_make_features(df_win)
            X = step6_scale_local(df_win, scaler)
            time_stats["4. Norm & Features (Step 4~6)"] += (time.perf_counter() - t3)

            # [구간 5] XGBoost 추론
            t4 = time.perf_counter()
            proba = model.predict_proba(X)
            score = calc_risk_score(proba)
            level = classify_level(score)
            time_stats["5. XGBoost Inference"] += (time.perf_counter() - t4)

            # [구간 6] Firestore 적재
            t5 = time.perf_counter()
            result_data = {
                "frame": frame_idx,
                "score": score,
                "level": level,
                "timestamp": time.time()
            }
            # 문서 ID를 frame 번호로 지정 (중복 방지)
            db.collection(COLLECTION_NAME).document(f"frame_{frame_idx}").set(result_data)
            time_stats["6. Firestore Insert"] += (time.perf_counter() - t5)

            inference_count += 1

        frame_idx += 1

    total_end_time = time.perf_counter()
    cap.release()
    pose.close()

    total_elapsed = total_end_time - total_start_time
    print("\n" + "=" * 50)
    print(" 📊 비디오 파이프라인 구간별 시간 프로파일링 결과")
    print("=" * 50)
    print(f"🎬 처리된 총 프레임 수: {frame_idx} 프레임")
    print(f"🚀 실행된 총 추론 횟수: {inference_count} 회")
    print(f"⏱️ 전체 E2E 소요 시간: {total_elapsed:.2f} 초")
    print("-" * 50)
    for step_name, step_time in time_stats.items():
        percentage = (step_time / total_elapsed) * 100
        print(f"[{step_name[:2]}] {step_name[3:]:27} : {step_time:6.2f}초 ({percentage:5.1f}%)")
    print("=" * 50)

    # Firestore에서 데이터 읽어서 검증
    print("\n📥 Firestore에서 저장된 데이터 샘플 조회 (최근 3건):")
    docs = db.collection(COLLECTION_NAME).order_by(
        "timestamp", direction=firestore.Query.DESCENDING
    ).limit(3).stream()
    for doc in docs:
        print(f"  {doc.id} → {doc.to_dict()}")


if __name__ == "__main__":
    # ✏️ [수정 필요] 테스트할 영상 파일명 (ai-server 폴더 기준 경로)
    run_full_profiling("fall_01.mp4")