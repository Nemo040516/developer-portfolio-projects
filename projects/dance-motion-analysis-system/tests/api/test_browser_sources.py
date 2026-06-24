import app.api.learner_preview as learner_preview
import app.api.learner_videos as learner_videos
import app.api.standard_preview as standard_preview
import app.api.standard_videos as standard_videos


def test_preview_endpoints_return_browser_source_urls(client, monkeypatch):
    monkeypatch.setattr(
        standard_preview,
        "build_standard_preview",
        lambda video_id, sample_fps: {"video": {"id": video_id}, "sample_fps": sample_fps, "frames": []},
    )
    monkeypatch.setattr(
        learner_preview,
        "build_learner_preview",
        lambda video_id, sample_fps: {"video": {"id": video_id}, "sample_fps": sample_fps, "frames": []},
    )

    standard_response = client.get("/api/videos/standard/1/preview?sample_fps=5")
    learner_response = client.get("/api/videos/learner/4/preview?sample_fps=5")

    assert standard_response.status_code == 200
    assert learner_response.status_code == 200
    assert standard_response.get_json()["data"]["preview"]["source_video_url"] == "/api/videos/standard/1/browser-source"
    assert standard_response.get_json()["data"]["preview"]["source_video_fallback_url"] == "/api/videos/standard/1/source"
    assert learner_response.get_json()["data"]["preview"]["source_video_url"] == "/api/videos/learner/4/browser-source"
    assert learner_response.get_json()["data"]["preview"]["source_video_fallback_url"] == "/api/videos/learner/4/source"


def test_browser_source_endpoints_send_cached_webm(client, monkeypatch, tmp_path):
    browser_source_path = tmp_path / "cached_browser_source.webm"
    browser_source_path.write_bytes(b"fake-browser-video")

    mock_standard_video = {"id": 1, "video_type": "standard"}
    mock_learner_video = {"id": 4, "video_type": "learner"}

    monkeypatch.setattr(standard_videos, "_find_standard_video", lambda video_id: mock_standard_video if video_id == 1 else None)
    monkeypatch.setattr(learner_videos, "get_video_record", lambda video_id: mock_learner_video if video_id == 4 else None)
    monkeypatch.setattr(standard_videos, "ensure_browser_video_source", lambda video: browser_source_path)
    monkeypatch.setattr(learner_videos, "ensure_browser_video_source", lambda video: browser_source_path)

    standard_response = client.get("/api/videos/standard/1/browser-source")
    learner_response = client.get("/api/videos/learner/4/browser-source")

    assert standard_response.status_code == 200
    assert learner_response.status_code == 200
    assert standard_response.mimetype == "video/webm"
    assert learner_response.mimetype == "video/webm"
    assert standard_response.data == browser_source_path.read_bytes()
    assert learner_response.data == browser_source_path.read_bytes()
