from pathlib import Path

from app.services import compare_service
from app.services import feedback_rule_service


def test_compare_analysis_videos_returns_perfect_score_for_same_content(monkeypatch):
    def fake_get_video_record(video_id):
        return {
            "id": video_id,
            "content_hash": "same-source-hash",
        }

    def fail_if_called(*args, **kwargs):
        raise AssertionError("same-source shortcut should skip motion metric computation")

    monkeypatch.setattr(compare_service, "get_video_record", fake_get_video_record)
    monkeypatch.setattr(compare_service, "build_standard_template", fail_if_called)
    monkeypatch.setattr(compare_service, "build_motion_metrics", fail_if_called)

    result = compare_service.compare_analysis_videos(
        analysis_id=17,
        standard_video_id=1,
        learner_video_id=2,
        sample_fps=5,
    )

    assert result["score"] == 100.0
    assert result["issues"] == []
    assert result["issue_segments"] == []
    assert all(value == 0.0 for value in result["joint_diffs"].values())
    assert all(value == 0.0 for value in result["trajectory_diffs"].values())


def test_calibrated_score_keeps_demo_pair_in_basic_match_range():
    score = compare_service.calculate_motion_score(
        {
            "left_elbow": 53.5299,
            "right_elbow": 52.2998,
            "left_knee": 7.7706,
            "right_knee": 7.6809,
        },
        {
            "left_hand": 0.5643,
            "right_hand": 0.5942,
            "left_foot": 0.236,
            "right_foot": 0.2556,
        },
    )

    assert score == 75.9


def test_calibrated_score_still_penalizes_large_mismatch():
    score = compare_service.calculate_motion_score(
        {
            "left_elbow": 100.0,
            "right_elbow": 100.0,
            "left_knee": 100.0,
            "right_knee": 100.0,
        },
        {
            "left_hand": 1.0,
            "right_hand": 1.0,
            "left_foot": 1.0,
            "right_foot": 1.0,
        },
    )

    assert score < 40.0


def test_compare_analysis_videos_uses_file_hash_fallback_for_same_content(monkeypatch):
    fixtures_dir = Path(__file__).resolve().parent.parent / "_tmp_compare_service"
    fixtures_dir.mkdir(parents=True, exist_ok=True)
    source_file = fixtures_dir / "same-video.mp4"
    learner_file = fixtures_dir / "same-video-copy.mp4"
    payload = b"same-video-binary"
    source_file.write_bytes(payload)
    learner_file.write_bytes(payload)

    def fake_get_video_record(video_id):
        return {
            "id": video_id,
            "file_path": f"storage/uploads/test/video_{video_id}.mp4",
        }

    def fake_resolve_video_file_path(video):
        return source_file if video["id"] == 1 else learner_file

    def fail_if_called(*args, **kwargs):
        raise AssertionError("same-source shortcut should skip motion metric computation")

    monkeypatch.setattr(compare_service, "get_video_record", fake_get_video_record)
    monkeypatch.setattr(compare_service, "resolve_video_file_path", fake_resolve_video_file_path)
    monkeypatch.setattr(compare_service, "build_standard_template", fail_if_called)
    monkeypatch.setattr(compare_service, "build_motion_metrics", fail_if_called)

    try:
        result = compare_service.compare_analysis_videos(
            analysis_id=18,
            standard_video_id=1,
            learner_video_id=2,
            sample_fps=5,
        )
    finally:
        source_file.unlink(missing_ok=True)
        learner_file.unlink(missing_ok=True)
        fixtures_dir.rmdir()

    assert result["score"] == 100.0
    assert result["issues"] == []


def _build_angle_frame(frame_index, timestamp_ms, angle_value):
    return {
        "frame_index": frame_index,
        "timestamp_ms": timestamp_ms,
        "angles": {
            "left_elbow": angle_value,
            "right_elbow": angle_value,
            "left_knee": angle_value,
            "right_knee": angle_value,
        },
    }


def _build_normalized_frame(frame_index, timestamp_ms, offset):
    return {
        "frame_index": frame_index,
        "timestamp_ms": timestamp_ms,
        "nodes": {
            "left_hand": {"x": 0.10 + offset, "y": 0.10},
            "right_hand": {"x": 0.30 + offset, "y": 0.10},
            "left_foot": {"x": 0.10 + offset, "y": 0.80},
            "right_foot": {"x": 0.30 + offset, "y": 0.80},
        },
    }


def test_trim_leading_inactive_frames_skips_intro_segment():
    angle_frames = [
        _build_angle_frame(0, 0, 10),
        _build_angle_frame(1, 1000, 10),
        _build_angle_frame(2, 2000, 10),
        _build_angle_frame(3, 3000, 10),
        _build_angle_frame(4, 4000, 40),
        _build_angle_frame(5, 5000, 60),
        _build_angle_frame(6, 6000, 80),
    ]
    normalized_frames = [
        _build_normalized_frame(0, 0, 0.00),
        _build_normalized_frame(1, 1000, 0.00),
        _build_normalized_frame(2, 2000, 0.00),
        _build_normalized_frame(3, 3000, 0.00),
        _build_normalized_frame(4, 4000, 0.20),
        _build_normalized_frame(5, 5000, 0.40),
        _build_normalized_frame(6, 6000, 0.60),
    ]

    trimmed_angles, trimmed_normalized = compare_service._trim_leading_inactive_frames(
        angle_frames,
        normalized_frames,
    )

    assert trimmed_angles[0]["timestamp_ms"] == 3000
    assert trimmed_normalized[0]["timestamp_ms"] == 3000
    assert len(trimmed_angles) == 4


def test_compare_analysis_videos_ignores_intro_length_difference(monkeypatch):
    standard_angle_frames = [
        _build_angle_frame(0, 0, 10),
        _build_angle_frame(1, 1000, 10),
        _build_angle_frame(2, 2000, 10),
        _build_angle_frame(3, 3000, 10),
        _build_angle_frame(4, 4000, 30),
        _build_angle_frame(5, 5000, 50),
        _build_angle_frame(6, 6000, 70),
        _build_angle_frame(7, 7000, 90),
    ]
    standard_normalized_frames = [
        _build_normalized_frame(0, 0, 0.00),
        _build_normalized_frame(1, 1000, 0.00),
        _build_normalized_frame(2, 2000, 0.00),
        _build_normalized_frame(3, 3000, 0.00),
        _build_normalized_frame(4, 4000, 0.20),
        _build_normalized_frame(5, 5000, 0.40),
        _build_normalized_frame(6, 6000, 0.60),
        _build_normalized_frame(7, 7000, 0.80),
    ]
    learner_angle_frames = [
        _build_angle_frame(0, 0, 10),
        _build_angle_frame(1, 1000, 30),
        _build_angle_frame(2, 2000, 50),
        _build_angle_frame(3, 3000, 70),
        _build_angle_frame(4, 4000, 90),
    ]
    learner_normalized_frames = [
        _build_normalized_frame(0, 0, 0.00),
        _build_normalized_frame(1, 1000, 0.20),
        _build_normalized_frame(2, 2000, 0.40),
        _build_normalized_frame(3, 3000, 0.60),
        _build_normalized_frame(4, 4000, 0.80),
    ]

    def fake_build_standard_template(video_id, sample_fps):
        return {
            "angles_path": "storage/outputs/templates/fake_angles.json",
            "normalized_data_path": "storage/outputs/templates/fake_norm.json",
        }

    def fake_load_json_payload(path):
        if path.endswith("fake_angles.json"):
            return {"frames": standard_angle_frames}
        if path.endswith("fake_norm.json"):
            return {"frames": standard_normalized_frames}
        raise AssertionError(f"unexpected path: {path}")

    def fake_build_motion_metrics(video_id, sample_fps):
        return {
            "angle_frames": learner_angle_frames,
            "normalized_frames": learner_normalized_frames,
        }

    monkeypatch.setattr(compare_service, "_is_same_source_video", lambda *_args, **_kwargs: False)
    monkeypatch.setattr(compare_service, "build_standard_template", fake_build_standard_template)
    monkeypatch.setattr(compare_service, "load_json_payload", fake_load_json_payload)
    monkeypatch.setattr(compare_service, "build_motion_metrics", fake_build_motion_metrics)

    result = compare_service.compare_analysis_videos(
        analysis_id=19,
        standard_video_id=1,
        learner_video_id=2,
        sample_fps=5,
    )

    assert result["score"] == 100.0
    assert all(value == 0.0 for value in result["joint_diffs"].values())
    assert all(value == 0.0 for value in result["trajectory_diffs"].values())


def test_segment_label_shows_standard_and_learner_time_when_intro_differs():
    label = feedback_rule_service._build_segment_label(
        {
            "start_timestamp_ms": 10000,
            "end_timestamp_ms": 11000,
            "center_timestamp_ms": 10500,
            "standard_start_timestamp_ms": 15000,
            "standard_end_timestamp_ms": 16000,
            "standard_center_timestamp_ms": 15500,
            "learner_start_timestamp_ms": 5000,
            "learner_end_timestamp_ms": 6000,
            "learner_center_timestamp_ms": 5500,
        }
    )

    assert label == "示范 15.00s - 16.00s / 练习 5.00s - 6.00s"
