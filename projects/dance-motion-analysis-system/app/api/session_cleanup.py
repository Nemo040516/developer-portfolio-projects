from flask import Blueprint

from app.services.transient_storage_service import purge_transient_storage_from_current_app

from .responses import ok_response


session_cleanup_bp = Blueprint("session_cleanup", __name__)


@session_cleanup_bp.post("/session/transient-cleanup")
def cleanup_transient_session_state():
    summary = purge_transient_storage_from_current_app()
    return ok_response({"cleanup": summary})
