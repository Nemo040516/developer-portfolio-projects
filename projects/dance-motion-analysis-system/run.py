import atexit

from app import create_app
from app.services.transient_storage_service import purge_transient_storage_from_current_app


app = create_app()


def _cleanup_transient_storage() -> None:
    with app.app_context():
        purge_transient_storage_from_current_app()


_cleanup_transient_storage()
atexit.register(_cleanup_transient_storage)


if __name__ == "__main__":
    app.run(debug=True)
