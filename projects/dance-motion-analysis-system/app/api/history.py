from flask import Blueprint, current_app

from app.services.analysis_records_service import get_analysis_detail_record, list_analysis_records
from app.services.mock_data_service import get_history_detail, get_history_items

from .responses import error_response, ok_response


history_bp = Blueprint("history", __name__)


@history_bp.get("/history")
def list_history():
    items = list_analysis_records(include_review_payload=True, saved_only=True)
    if not items and current_app.config.get("ENABLE_MOCK_DATA", False):
        items = [
            get_history_detail(item["id"]) or item
            for item in get_history_items()
        ]
    return ok_response({"items": items})


@history_bp.get("/history/<int:history_id>")
def get_history(history_id: int):
    history_detail = get_analysis_detail_record(history_id, ensure_result=True)
    if history_detail is not None and not history_detail.get("is_saved"):
        history_detail = None
    if history_detail is None and current_app.config.get("ENABLE_MOCK_DATA", False):
        history_detail = get_history_detail(history_id)
    if history_detail is None:
        return error_response(4042, "history not found", status=404)
    return ok_response({"history": history_detail})
