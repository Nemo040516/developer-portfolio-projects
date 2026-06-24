from pathlib import Path

import cv2


def read_video_metadata(file_path: Path) -> dict[str, float | int]:
    capture = cv2.VideoCapture(str(file_path))
    try:
        if not capture.isOpened():
            raise ValueError("invalid video file")

        frame_rate = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
        frame_count = float(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0.0)
        width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
        height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)

        if width <= 0 or height <= 0:
            raise ValueError("invalid video file")

        duration_sec = frame_count / frame_rate if frame_rate > 0 else 0.0

        return {
            "duration_sec": round(duration_sec, 1),
            "frame_rate": round(frame_rate, 1),
            "width": width,
            "height": height,
        }
    finally:
        capture.release()
