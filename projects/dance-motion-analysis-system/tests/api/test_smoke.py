import json

import app.api.analysis_tasks as analysis_tasks_api

import pytest


@pytest.mark.api
@pytest.mark.parametrize(
    "path",
    [
        "/",
        "/session",
        "/analysis",
        "/review",
        "/history",
    ],
)
def test_web_pages_return_success(client, path):
    response = client.get(path)

    assert response.status_code == 200


@pytest.mark.api
def test_session_page_exposes_role_confirmation_controls(client):
    response = client.get("/session")

    assert response.status_code == 200
    html = response.get_data(as_text=True)
    assert "导入角色确认" in html
    assert "交换角色" in html
    assert 'name="session-single-role"' in html


@pytest.mark.api
def test_health_endpoint_returns_running_status(client):
    response = client.get("/api/health")

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["code"] == 0
    assert payload["data"]["status"] == "running"


@pytest.mark.api
def test_create_analysis_endpoint_generates_and_saves_ready_result(client, monkeypatch):
    calls = []

    def fake_create_analysis_record(standard_video_id, learner_video_id, sample_fps):
        calls.append(("create", standard_video_id, learner_video_id, sample_fps))
        return {"id": 9, "status": "pending", "is_saved": False}

    def fake_get_analysis_detail_record(analysis_id, ensure_result=False):
        calls.append(("detail", analysis_id, ensure_result))
        return {"id": analysis_id, "status": "success", "is_saved": False}

    def fake_save_analysis_record(analysis_id):
        calls.append(("save", analysis_id))
        return {"id": analysis_id, "status": "success", "is_saved": True}

    monkeypatch.setattr(analysis_tasks_api, "create_analysis_record", fake_create_analysis_record)
    monkeypatch.setattr(analysis_tasks_api, "get_analysis_detail_record", fake_get_analysis_detail_record)
    monkeypatch.setattr(analysis_tasks_api, "save_analysis_record", fake_save_analysis_record)

    response = client.post(
        "/api/analysis",
        json={"standard_video_id": 1, "learner_video_id": 2, "sample_fps": 5},
    )

    assert response.status_code == 201
    payload = response.get_json()
    assert payload["code"] == 0
    assert payload["data"]["analysis"] == {"id": 9, "status": "success", "is_saved": True}
    assert calls == [
        ("create", 1, 2, 5),
        ("detail", 9, True),
        ("save", 9),
    ]


@pytest.mark.api
def test_transient_cleanup_endpoint_returns_success(client, app, tmp_path):
    storage_root = tmp_path / "storage"
    video_records_file = storage_root / "records" / "videos.json"
    analysis_records_file = storage_root / "records" / "analysis_records.json"
    upload_file = storage_root / "uploads" / "learner" / "sample.mp4"
    unsaved_result_file = storage_root / "outputs" / "comparisons" / "analysis_2.json"

    upload_file.parent.mkdir(parents=True, exist_ok=True)
    upload_file.write_text("temp", encoding="utf-8")
    unsaved_result_file.parent.mkdir(parents=True, exist_ok=True)
    unsaved_result_file.write_text('{"score": 81.0}', encoding="utf-8")
    video_records_file.parent.mkdir(parents=True, exist_ok=True)
    video_records_file.write_text('[{"id": 1}]', encoding="utf-8")
    analysis_records_file.write_text(
        '[{"id": 2, "is_saved": false, "result_json_path": "storage/outputs/comparisons/analysis_2.json"}]',
        encoding="utf-8",
    )

    app.config.update(
        STORAGE_ROOT=storage_root,
        VIDEO_RECORDS_FILE=video_records_file,
        ANALYSIS_RECORDS_FILE=analysis_records_file,
    )

    response = client.post("/api/session/transient-cleanup", json={})

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["code"] == 0
    assert payload["data"]["cleanup"]["cleared_video_records"] == 1
    assert payload["data"]["cleanup"]["cleared_unsaved_analysis_records"] == 1


@pytest.mark.api
def test_save_analysis_endpoint_returns_success(client, monkeypatch):
    monkeypatch.setattr(
        analysis_tasks_api,
        "save_analysis_record",
        lambda analysis_id: {"id": analysis_id, "status": "success", "is_saved": True},
    )

    response = client.post("/api/analysis/7/save", json={})

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["code"] == 0
    assert payload["data"]["analysis"]["id"] == 7
    assert payload["data"]["analysis"]["is_saved"] is True


@pytest.mark.api
def test_history_endpoint_only_returns_saved_items(client, app, tmp_path):
    storage_root = tmp_path / "storage"
    analysis_records_file = storage_root / "records" / "analysis_records.json"
    analysis_records_file.parent.mkdir(parents=True, exist_ok=True)
    analysis_records_file.write_text(
        """
[
  {"id": 1, "status": "success", "is_saved": true, "updated_at": "2026-03-30 20:00:00", "created_at": "2026-03-30 20:00:00", "result_json_path": null},
  {"id": 2, "status": "success", "is_saved": false, "updated_at": "2026-03-30 20:05:00", "created_at": "2026-03-30 20:05:00", "result_json_path": null}
]
        """.strip(),
        encoding="utf-8",
    )

    app.config.update(
        STORAGE_ROOT=storage_root,
        ANALYSIS_RECORDS_FILE=analysis_records_file,
    )

    response = client.get("/api/history")

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["code"] == 0
    assert [item["id"] for item in payload["data"]["items"]] == [1]


@pytest.mark.api
def test_history_endpoint_calibrates_legacy_result_score(client, app, tmp_path):
    storage_root = tmp_path / "storage"
    analysis_records_file = storage_root / "records" / "analysis_records.json"
    result_file = storage_root / "outputs" / "comparisons" / "analysis_1.json"
    result_file.parent.mkdir(parents=True, exist_ok=True)
    analysis_records_file.parent.mkdir(parents=True, exist_ok=True)
    analysis_records_file.write_text(
        """
[
  {"id": 1, "status": "success", "score": 49.1, "is_saved": true, "updated_at": "2026-05-04 20:48:16", "created_at": "2026-05-04 20:48:14", "result_json_path": "storage/outputs/comparisons/analysis_1.json"}
]
        """.strip(),
        encoding="utf-8",
    )
    result_file.write_text(
        json.dumps(
            {
                "score": 49.1,
                "joint_diffs": {
                    "left_elbow": 53.5299,
                    "right_elbow": 52.2998,
                    "left_knee": 7.7706,
                    "right_knee": 7.6809,
                },
                "trajectory_diffs": {
                    "left_hand": 0.5643,
                    "right_hand": 0.5942,
                    "left_foot": 0.236,
                    "right_foot": 0.2556,
                },
                "issues": [],
                "issue_segments": [],
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    app.config.update(
        STORAGE_ROOT=storage_root,
        ANALYSIS_RECORDS_FILE=analysis_records_file,
    )

    response = client.get("/api/history")

    assert response.status_code == 200
    payload = response.get_json()
    item = payload["data"]["items"][0]
    assert item["score"] == 75.9
    assert item["summary_text"].startswith("整体动作基本接近标准视频")


@pytest.mark.api
@pytest.mark.parametrize(
    "path, key",
    [
        ("/api/videos/standard", "items"),
        ("/api/videos/learner", "items"),
        ("/api/history", "items"),
    ],
)
def test_list_endpoints_return_success_payload(client, path, key):
    response = client.get(path)

    assert response.status_code == 200
    payload = response.get_json()
    assert payload["code"] == 0
    assert key in payload["data"]
