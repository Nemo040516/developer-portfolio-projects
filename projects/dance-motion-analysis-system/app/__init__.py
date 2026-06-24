from pathlib import Path

from flask import Flask

from .api import api_bp
from .config import Config
from .web import web_bp


def create_app() -> Flask:
    app = Flask(__name__, template_folder="templates", static_folder="static")
    app.config.from_object(Config)

    _ensure_storage_dirs()

    app.register_blueprint(web_bp)
    app.register_blueprint(api_bp, url_prefix="/api")
    return app


def _ensure_storage_dirs() -> None:
    storage_dirs = [
        Path(Config.MODEL_ROOT),
        Path(Config.STORAGE_ROOT) / "uploads" / "standard",
        Path(Config.STORAGE_ROOT) / "uploads" / "learner",
        Path(Config.STORAGE_ROOT) / "records",
        Path(Config.STORAGE_ROOT) / "outputs" / "keypoints",
        Path(Config.STORAGE_ROOT) / "outputs" / "templates",
        Path(Config.STORAGE_ROOT) / "outputs" / "comparisons",
        Path(Config.STORAGE_ROOT) / "outputs" / "previews",
        Path(Config.STORAGE_ROOT) / "outputs" / "thumbnails" / "standard",
        Path(Config.STORAGE_ROOT) / "outputs" / "thumbnails" / "learner",
        Path(Config.STORAGE_ROOT) / "outputs" / "browser_sources" / "standard",
        Path(Config.STORAGE_ROOT) / "outputs" / "browser_sources" / "learner",
    ]

    for directory in storage_dirs:
        directory.mkdir(parents=True, exist_ok=True)

    for file_path in (Config.VIDEO_RECORDS_FILE, Config.ANALYSIS_RECORDS_FILE):
        if not file_path.exists():
            file_path.write_text("[]", encoding="utf-8")
