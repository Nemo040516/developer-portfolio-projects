from flask import Blueprint, current_app, request

from app.services.analysis_records_service import create_analysis_record, get_analysis_detail_record, save_analysis_record
from app.services.mock_data_service import get_analysis_detail

from .responses import error_response, ok_response


analysis_tasks_bp = Blueprint("analysis_tasks", __name__)


@analysis_tasks_bp.get("/analysis/<int:analysis_id>")
def get_analysis(analysis_id: int):
    analysis = get_analysis_detail_record(analysis_id, ensure_result=True)
    if analysis is None and current_app.config.get("ENABLE_MOCK_DATA", False):
        analysis = get_analysis_detail(analysis_id)
    if analysis is None:
        return error_response(4041, "analysis not found", status=404)
    return ok_response({"analysis": analysis})


@analysis_tasks_bp.post("/analysis")
def create_analysis():
    payload = request.get_json(silent=True) or {}
    standard_video_id = payload.get("standard_video_id")
    learner_video_id = payload.get("learner_video_id")
    sample_fps = payload.get("sample_fps", current_app.config["DEFAULT_SAMPLE_FPS"])

    if standard_video_id is None or learner_video_id is None:
        return error_response(4000, "missing video id")

    try:
        standard_video_id = int(standard_video_id)
        learner_video_id = int(learner_video_id)
        sample_fps = int(sample_fps)
    except (TypeError, ValueError):
        return error_response(4000, "invalid video id")

    try:
        analysis = create_analysis_record(
            standard_video_id,
            learner_video_id,
            sample_fps,
        )
        analysis = get_analysis_detail_record(int(analysis["id"]), ensure_result=True) or analysis
        if analysis.get("status") == "success":
            analysis = save_analysis_record(int(analysis["id"]))
    except ValueError:
        return error_response(4003, "invalid sample fps")
    except LookupError as error:
        message = str(error)
        code = 4044 if message == "invalid standard video id" else 4045
        return error_response(code, message, status=404)

    return ok_response({"analysis": analysis}, status=201)


@analysis_tasks_bp.post("/analysis/<int:analysis_id>/save")
def save_analysis(analysis_id: int):
    try:
        analysis = save_analysis_record(analysis_id)
    except LookupError:
        return error_response(4041, "analysis not found", status=404)
    except ValueError:
        return error_response(4004, "analysis not ready to save")

    return ok_response({"analysis": analysis})
