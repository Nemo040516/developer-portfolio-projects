from app.services.video_name_service import build_video_display_name


def test_build_video_display_name_uses_fallback_for_machine_like_names():
    assert build_video_display_name("96eef886305472b9d14f7f17f2701d27.mp4", "standard", 1) == "示范动作 01"


def test_build_video_display_name_hides_demo_sample_style_names():
    assert build_video_display_name("learner_temporal_warp_sample.mp4", "learner", 4) == "练习视频 04"


def test_build_video_display_name_keeps_human_readable_name():
    assert build_video_display_name("wave_combo_reference.mp4", "standard", 1) == "wave combo reference"
