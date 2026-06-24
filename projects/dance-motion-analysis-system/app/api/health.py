from flask import Blueprint, current_app

from .responses import ok_response

health_bp = Blueprint("health", __name__)


@health_bp.get("/health")
def health():
    return ok_response(
        {
            "service": current_app.config["APP_NAME"],
            "status": "running",
        }
    )
