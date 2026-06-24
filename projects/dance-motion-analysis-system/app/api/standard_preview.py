from flask import Blueprint, current_app, request, url_for

from app.services.standard_preview_service import build_standard_preview

from .responses import error_response, ok_response


standard_preview_bp = Blueprint("standard_preview", __name__)


@standard_preview_bp.get("/videos/standard/<int:video_id>/preview")
def get_standard_preview(video_id: int):
    raw_sample_fps = request.args.get("sample_fps", current_app.config["DEFAULT_SAMPLE_FPS"])

    try:
        sample_fps = int(raw_sample_fps)
    except (TypeError, ValueError):
        return error_response(4003, "invalid sample fps")

    try:
        preview = build_standard_preview(video_id, sample_fps)
        preview["source_video_url"] = url_for(
            "api.standard_videos.get_standard_browser_source",
            video_id=video_id,
        )
        preview["source_video_fallback_url"] = url_for(
            "api.standard_videos.get_standard_video_source",
            video_id=video_id,
        )
    except ValueError:
        return error_response(4003, "invalid sample fps")
    except LookupError:
        return error_response(4044, "invalid standard video id", status=404)
    except FileNotFoundError:
        return error_response(4046, "preview source file not found", status=404)

    return ok_response({"preview": preview})
