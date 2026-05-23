"""
WebSocket 추론 서버 동작 확인용 더미 클라이언트.

사용:
    # 합성 데이터(랜덤 좌표)로 동작 — 모델 동작만 확인
    python ws_test_client.py

    # 학습 노트북에서 만든 wide-format landmark CSV 로 동작
    # CSV 컬럼: frame, timestamp, kp0_x..kp32_visibility
    python ws_test_client.py path/to/landmarks.csv
"""

import asyncio
import json
import sys
import websockets
import pandas as pd
import numpy as np

SERVER_URL = "ws://localhost:8000/ws/predict"
FPS = 15  # 폰이 보낸다고 가정하는 fps


def row_to_frame_msg(row, frame_idx: int) -> dict:
    """CSV 한 행을 frame 메시지로 변환"""
    landmarks = []
    for i in range(33):
        landmarks.append({
            "x": float(row[f"kp{i}_x"]),
            "y": float(row[f"kp{i}_y"]),
            "z": float(row[f"kp{i}_z"]),
            "v": float(row[f"kp{i}_visibility"]),
        })
    return {
        "type": "frame",
        "frame": frame_idx,
        "timestamp": frame_idx / FPS,
        "landmarks": landmarks,
    }


def synthetic_frames(n: int = 200):
    """가짜 landmark — 정적 포즈 + 약간 흔들기 (서버 동작 확인용)"""
    base = np.random.rand(33, 4) * 0.5 + 0.25
    for i in range(n):
        coords = base + np.random.randn(33, 4) * 0.01
        yield {
            "type": "frame",
            "frame": i,
            "timestamp": i / FPS,
            "landmarks": [
                {"x": float(coords[j, 0]), "y": float(coords[j, 1]),
                 "z": float(coords[j, 2]), "v": float(coords[j, 3])}
                for j in range(33)
            ],
        }


async def send_frames(ws, csv_path):
    if csv_path:
        df = pd.read_csv(csv_path)
        for idx, row in df.iterrows():
            await ws.send(json.dumps(row_to_frame_msg(row, int(idx))))
            await asyncio.sleep(1 / FPS)
    else:
        for msg in synthetic_frames():
            await ws.send(json.dumps(msg))
            await asyncio.sleep(1 / FPS)
    print("[FRAMES] 송신 완료")


async def recv_results(ws):
    while True:
        try:
            msg = await ws.recv()
        except websockets.ConnectionClosed:
            break
        data = json.loads(msg)
        if data.get("type") == "result":
            print(f"[RESULT] frame={data['frame']:>5}  "
                  f"score={data['fall_score']:6.2f}  "
                  f"level={data['level']}")


async def main(csv_path: str | None):
    async with websockets.connect(SERVER_URL) as ws:
        # 1) init
        await ws.send(json.dumps({
            "type": "init",
            "device_id": "test_device_001",
            "session_token": "dummy_token",
        }))
        print(f"[INIT] {SERVER_URL} 연결")

        # 2) 송수신 동시 실행
        await asyncio.gather(send_frames(ws, csv_path), recv_results(ws))


if __name__ == "__main__":
    csv = sys.argv[1] if len(sys.argv) > 1 else None
    try:
        asyncio.run(main(csv))
    except KeyboardInterrupt:
        pass