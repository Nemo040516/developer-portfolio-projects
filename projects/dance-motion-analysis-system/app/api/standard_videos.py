from flask import Blueprint, current_app, request, send_file, url_for

from app.services.browser_video_source_service import ensure_browser_video_source
from app.services.mock_data_service import get_standard_video_items
from app.services.video_records_service import (
    create_video_record,
    ensure_video_thumbnail_path,
    get_video_record,
    list_video_records,
    resolve_thumbnail_file_path,
    resolve_video_file_path,
)

from .responses import error_response, ok_response


standard_videos_bp = Blueprint("standard_videos", __name__)


def _find_standard_video(video_id: int) -> dict | None:
    video = get_video_record(video_id)
    if video is not None and video["video_type"] != "standard":
        video = None
    if video is None:
        if not current_app.config.get("ENABLE_MOCK_DATA", False):
            return None
        video = next(
            (item for item in get_standard_video_items() if item["id"] == video_id),
            None,
        )
    return video


def _serialize_standard_video_item(video: dict) -> dict:
    payload = dict(video)
    thumbnail_path = ensure_video_thumbnail_path(payload)
    payload["thumbnail_path"] = thumbnail_path
    payload["thumbnail_url"] = (
        url_for("api.standard_videos.get_standard_video_thumbnail", video_id=payload["id"])
        if thumbnail_path
        else None
    )
    return payload


@standard_videos_bp.get("/videos/standard")
def list_standard_videos():
    items = list_video_records("standard")
    if not items and current_app.config.get("ENABLE_MOCK_DATA", False):
        items = get_standard_video_items()
    return ok_response({"items": [_serialize_standard_video_item(item) for item in items]})


@standard_videos_bp.get("/videos/standard/<int:video_id>")
def get_standard_video(video_id: int):
    video = _find_standard_video(video_id)
    if video is None:
        return error_response(4043, "video not found", status=404)
    return ok_response({"video": _serialize_standard_video_item(video)})


@standard_videos_bp.get("/videos/standard/<int:video_id>/source")
def get_standard_video_source(video_id: int):
    video = _find_standard_video(video_id)
    if video is None:
        return error_response(4043, "video not found", status=404)

    video_path = resolve_video_file_path(video)
    if not video_path.exists():
        return error_response(4046, "video source file not found", status=404)

    return send_file(video_path, conditional=True)


@standard_videos_bp.get("/videos/standard/<int:video_id>/browser-source")
def get_standard_browser_source(video_id: int):
    video = _find_standard_video(video_id)
    if video is None:
        return error_response(4043, "video not found", status=404)

    try:
        browser_source_path = ensure_browser_video_source(video)
    except FileNotFoundError:
        return error_response(4046, "video source file not found", status=404)
    except RuntimeError:
        return error_response(5004, "browser compatible video source build failed", status=500)

    return send_file(browser_source_path, conditional=True, mimetype="video/webm")


@standard_videos_bp.get("/videos/standard/<int:video_id>/thumbnail")
def get_standard_video_thumbnail(video_id: int):
    video = _find_standard_video(video_id)
    if video is None:
        return error_response(4043, "video not found", status=404)

    thumbnail_path = ensure_video_thumbnail_path(video)
    if not thumbnail_path:
        return error_response(4047, "video thumbnail not found", status=404)

    video["thumbnail_path"] = thumbnail_path
    absolute_thumbnail_path = resolve_thumbnail_file_path(video)
    if not absolute_thumbnail_path.exists():
        return error_response(4047, "video thumbnail not found", status=404)

    return send_file(absolute_thumbnail_path, conditional=True)


@standard_videos_bp.post("/videos/standard")
def upload_standard_video():
    file = request.files.get("file")
    if file is None:
        return error_response(4000, "missing file")

    try:
        video = create_video_record(file, "standard")
    except ValueError as error:
        message = str(error)
        code = 4001 if message == "invalid file type" else 4000
        return error_response(code, message)
    except Exception:
        return error_response(4002, "invalid video file")

    return ok_response({"video": _serialize_standard_video_item(video)}, status=201)
