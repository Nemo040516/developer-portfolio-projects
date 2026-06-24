from flask import Blueprint

from .analysis_tasks import analysis_tasks_bp
from .health import health_bp
from .learner_preview import learner_preview_bp
from .history import history_bp
from .learner_videos import learner_videos_bp
from .session_cleanup import session_cleanup_bp
from .standard_preview import standard_preview_bp
from .standard_videos import standard_videos_bp


api_bp = Blueprint("api", __name__)
api_bp.register_blueprint(standard_videos_bp)
api_bp.register_blueprint(standard_preview_bp)
api_bp.register_blueprint(learner_videos_bp)
api_bp.register_blueprint(learner_preview_bp)
api_bp.register_blueprint(analysis_tasks_bp)
api_bp.register_blueprint(history_bp)
api_bp.register_blueprint(session_cleanup_bp)
api_bp.register_blueprint(health_bp)
