import hashlib
import json

from app.services import video_records_service


def test_list_video_records_backfills_content_hash(app, monkeypatch, tmp_path):
    storage_root = tmp_path / "storage"
    upload_dir = storage_root / "uploads" / "standard"
    upload_dir.mkdir(parents=True)
    video_path = upload_dir / "sample.mp4"
    video_bytes = b"same-video-content"
    video_path.write_bytes(video_bytes)

    records_file = tmp_path / "videos.json"
    records_file.write_text(
        json.dumps(
            [
                {
                    "id": 1,
                    "video_type": "standard",
                    "original_filename": "sample.mp4",
                    "display_name": "示范动作 01",
                    "stored_filename": "sample.mp4",
                    "file_path": "storage/uploads/standard/sample.mp4",
                    "created_at": "2026-03-30 10:00:00",
                }
            ],
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    monkeypatch.setattr(video_records_service, "ensure_video_thumbnail_path", lambda record: None)

    app.config.update(
        STORAGE_ROOT=storage_root,
        VIDEO_RECORDS_FILE=records_file,
    )

    with app.app_context():
        items = video_records_service.list_video_records()

    expected_hash = hashlib.sha256(video_bytes).hexdigest()
    assert items[0]["content_hash"] == expected_hash

    persisted_records = json.loads(records_file.read_text(encoding="utf-8"))
    assert persisted_records[0]["content_hash"] == expected_hash
