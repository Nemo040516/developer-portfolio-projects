import json

from app.services.transient_storage_service import purge_transient_storage


def test_purge_transient_storage_clears_temp_files_but_keeps_results(tmp_path):
    storage_root = tmp_path / "storage"
    video_records_file = storage_root / "records" / "videos.json"
    analysis_records_file = storage_root / "records" / "analysis_records.json"
    comparisons_dir = storage_root / "outputs" / "comparisons"

    transient_paths = [
        storage_root / "uploads" / "standard" / "standard.mp4",
        storage_root / "uploads" / "learner" / "learner.mp4",
        storage_root / "outputs" / "keypoints" / "video_1_5fps.json",
        storage_root / "outputs" / "templates" / "video_1_5fps_template.json",
        storage_root / "outputs" / "previews" / "learner_video_1_5fps_norm.json",
        storage_root / "outputs" / "thumbnails" / "standard" / "standard.jpg",
        storage_root / "outputs" / "browser_sources" / "learner" / "learner.webm",
    ]

    for path in transient_paths:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("temp", encoding="utf-8")

    comparisons_dir.mkdir(parents=True, exist_ok=True)
    saved_comparison_file = comparisons_dir / "analysis_1.json"
    saved_comparison_file.write_text('{"score": 92.0}', encoding="utf-8")
    unsaved_comparison_file = comparisons_dir / "analysis_2.json"
    unsaved_comparison_file.write_text('{"score": 81.0}', encoding="utf-8")

    video_records_file.parent.mkdir(parents=True, exist_ok=True)
    video_records_file.write_text(
        json.dumps([{"id": 1, "file_path": "storage/uploads/standard/standard.mp4"}], ensure_ascii=False),
        encoding="utf-8",
    )
    analysis_records_file.write_text(
        json.dumps(
            [
                {"id": 1, "is_saved": True, "result_json_path": "storage/outputs/comparisons/analysis_1.json"},
                {"id": 2, "is_saved": False, "result_json_path": "storage/outputs/comparisons/analysis_2.json"},
            ],
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )

    summary = purge_transient_storage(storage_root, video_records_file, analysis_records_file)

    assert summary["cleared_files"] >= len(transient_paths)
    assert summary["cleared_video_records"] == 1
    assert summary["cleared_unsaved_analysis_records"] == 1
    assert summary["cleared_unsaved_result_files"] == 1
    assert json.loads(video_records_file.read_text(encoding="utf-8")) == []
    assert saved_comparison_file.exists()
    assert not unsaved_comparison_file.exists()
    assert json.loads(analysis_records_file.read_text(encoding="utf-8")) == [
        {"id": 1, "is_saved": True, "result_json_path": "storage/outputs/comparisons/analysis_1.json"}
    ]
    for path in transient_paths:
        assert not path.exists()
