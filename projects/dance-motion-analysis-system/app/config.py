import os
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent.parent


class Config:
    APP_NAME = "dance-motion-analysis-system"
    PRODUCT_NAME = "舞蹈动作视频比对与纠错系统"
    PRODUCT_TAGLINE = "先选示范动作，再上传练习视频，快速看到差异与调整建议。"
    SECRET_KEY = os.getenv("FLASK_SECRET_KEY", "portfolio-demo-secret")
    ENABLE_MOCK_DATA = False
    STORAGE_ROOT = BASE_DIR / "storage"
    MODEL_ROOT = STORAGE_ROOT / "models"
    RECORDS_ROOT = STORAGE_ROOT / "records"
    VIDEO_RECORDS_FILE = RECORDS_ROOT / "videos.json"
    ANALYSIS_RECORDS_FILE = RECORDS_ROOT / "analysis_records.json"
    POSE_LANDMARKER_MODEL_PATH = MODEL_ROOT / "pose_landmarker_lite.task"
    POSE_LANDMARKER_MODEL_URL = (
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
        "pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
    )
    DEFAULT_SAMPLE_FPS = 5
    ALLOWED_SAMPLE_FPS = (5, 8, 10)
    ALLOWED_VIDEO_EXTENSIONS = ("mp4", "mov", "avi", "mkv")
    MAX_CONTENT_LENGTH = 100 * 1024 * 1024
