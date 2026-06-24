from flask import Blueprint, current_app, request, url_for

from app.services.standard_preview_service import build_learner_preview

from .responses import error_response, ok_response


learner_preview_bp = Blueprint("learner_preview", __name__)


@learner_preview_bp.get("/videos/learner/<int:video_id>/preview")
def get_learner_preview(video_id: int):
    raw_sample_fps = request.args.get("sample_fps", current_app.config["DEFAULT_SAMPLE_FPS"])

    try:
        sample_fps = int(raw_sample_fps)
    except (TypeError, ValueError):
        return error_response(4003, "invalid sample fps")

    try:
        preview = build_learner_preview(video_id, sample_fps)
        preview["source_video_url"] = url_for(
            "api.learner_videos.get_learner_browser_source",
            video_id=video_id,
        )
        preview["source_video_fallback_url"] = url_for(
            "api.learner_videos.get_learner_video_source",
            video_id=video_id,
        )
    except ValueError:
        return error_response(4003, "invalid sample fps")
    except LookupError:
        return error_response(4045, "invalid learner video id", status=404)
    except FileNotFoundError:
        return error_response(4046, "preview source file not found", status=404)

    return ok_response({"preview": preview})
