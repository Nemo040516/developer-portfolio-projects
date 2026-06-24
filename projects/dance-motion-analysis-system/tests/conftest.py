import shutil
from pathlib import Path
from uuid import uuid4

import pytest

from app import create_app


@pytest.fixture()
def app():
    app = create_app()
    app.config.update(TESTING=True)
    return app


@pytest.fixture()
def client(app):
    return app.test_client()


@pytest.fixture()
def tmp_path():
    workspace_tmp_root = Path(__file__).resolve().parents[1] / ".tmp_pytest"
    workspace_tmp_root.mkdir(parents=True, exist_ok=True)

    path = workspace_tmp_root / uuid4().hex
    path.mkdir(parents=True, exist_ok=True)
    try:
        yield path
    finally:
        shutil.rmtree(path, ignore_errors=True)
