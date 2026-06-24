from flask import Blueprint, request, send_file, url_for

from app.services.browser_video_source_service import ensure_browser_video_source
from app.services.video_records_service import (
    create_video_record,
    ensure_video_thumbnail_path,
    get_video_record,
    list_video_records,
    resolve_thumbnail_file_path,
    resolve_video_file_path,
)

from .responses import error_response, ok_response


learner_videos_bp = Blueprint("learner_videos", __name__)


def _serialize_learner_video_item(video: dict) -> dict:
    payload = dict(video)
    thumbnail_path = ensure_video_thumbnail_path(payload)
    payload["thumbnail_path"] = thumbnail_path
    payload["thumbnail_url"] = (
        url_for("api.learner_videos.get_learner_video_thumbnail", video_id=payload["id"])
        if thumbnail_path
        else None
    )
    return payload


@learner_videos_bp.get("/videos/learner")
def list_learner_videos():
    items = sorted(
        list_video_records("learner"),
        key=lambda item: int(item["id"]),
        reverse=True,
    )
    return ok_response({"items": [_serialize_learner_video_item(item) for item in items]})


@learner_videos_bp.get("/videos/learner/<int:video_id>/source")
def get_learner_video_source(video_id: int):
    video = get_video_record(video_id)
    if video is None or video["video_type"] != "learner":
        return error_response(4045, "invalid learner video id", status=404)

    video_path = resolve_video_file_path(video)
    if not video_path.exists():
        return error_response(4046, "video source file not found", status=404)

    return send_file(video_path, conditional=True)


@learner_videos_bp.get("/videos/learner/<int:video_id>/browser-source")
def get_learner_browser_source(video_id: int):
    video = get_video_record(video_id)
    if video is None or video["video_type"] != "learner":
        return error_response(4045, "invalid learner video id", status=404)

    try:
        browser_source_path = ensure_browser_video_source(video)
    except FileNotFoundError:
        return error_response(4046, "video source file not found", status=404)
    except RuntimeError:
        return error_response(5003, "browser compatible video source build failed", status=500)

    return send_file(browser_source_path, conditional=True, mimetype="video/webm")


@learner_videos_bp.get("/videos/learner/<int:video_id>/thumbnail")
def get_learner_video_thumbnail(video_id: int):
    video = get_video_record(video_id)
    if video is None or video["video_type"] != "learner":
        return error_response(4045, "invalid learner video id", status=404)

    thumbnail_path = ensure_video_thumbnail_path(video)
    if not thumbnail_path:
        return error_response(4047, "video thumbnail not found", status=404)

    video["thumbnail_path"] = thumbnail_path
    absolute_thumbnail_path = resolve_thumbnail_file_path(video)
    if not absolute_thumbnail_path.exists():
        return error_response(4047, "video thumbnail not found", status=404)

    return send_file(absolute_thumbnail_path, conditional=True)


@learner_videos_bp.post("/videos/learner")
def upload_learner_video():
    file = request.files.get("file")
    if file is None:
        return error_response(4000, "missing file")

    try:
        video = create_video_record(file, "learner")
    except ValueError as error:
        message = str(error)
        code = 4001 if message == "invalid file type" else 4000
        return error_response(code, message)
    except Exception:
        return error_response(4002, "invalid video file")

    return ok_response({"video": _serialize_learner_video_item(video)}, status=201)
