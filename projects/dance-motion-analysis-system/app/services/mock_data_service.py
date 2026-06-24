from copy import deepcopy
from typing import Any

from flask import current_app

from .video_name_service import build_video_display_name


def get_standard_video_items() -> list[dict[str, Any]]:
    items = [
        {
            "id": 1,
            "video_type": "standard",
            "original_filename": "standard_intro_combo.mp4",
            "display_name": build_video_display_name("standard_intro_combo.mp4", "standard", 1),
            "stored_filename": "20260312_std_001.mp4",
            "file_path": "storage/uploads/standard/20260312_std_001.mp4",
            "duration_sec": 18.6,
            "frame_rate": 30.0,
            "width": 1280,
            "height": 720,
            "created_at": "2026-03-12 14:30:00",
        },
        {
            "id": 2,
            "video_type": "standard",
            "original_filename": "standard_side_step.mp4",
            "display_name": build_video_display_name("standard_side_step.mp4", "standard", 2),
            "stored_filename": "20260312_std_002.mp4",
            "file_path": "storage/uploads/standard/20260312_std_002.mp4",
            "duration_sec": 24.0,
            "frame_rate": 30.0,
            "width": 1280,
            "height": 720,
            "created_at": "2026-03-12 14:32:00",
        },
    ]
    return deepcopy(items)


def get_analysis_detail(analysis_id: int) -> dict[str, Any] | None:
    default_sample_fps = current_app.config["DEFAULT_SAMPLE_FPS"]
    details = {
        1: {
            "id": 1,
            "standard_video_id": 1,
            "learner_video_id": 2,
            "sample_fps": default_sample_fps,
            "status": "success",
            "score": 82.5,
            "result_json_path": "storage/outputs/comparisons/analysis_1.json",
            "summary_text": "整体动作接近标准视频，左肘和右膝偏差较明显。",
            "created_at": "2026-03-12 14:40:00",
            "updated_at": "2026-03-12 14:40:20",
            "joint_diffs": {
                "left_elbow": 18.2,
                "right_elbow": 10.4,
                "left_knee": 7.9,
                "right_knee": 16.8,
            },
            "trajectory_diffs": {
                "left_hand": 0.21,
                "right_hand": 0.13,
                "left_foot": 0.11,
                "right_foot": 0.18,
            },
            "suggestions": [
                "左肘弯曲不足",
                "右膝抬起幅度偏低",
            ],
        }
    }
    detail = details.get(analysis_id)
    return deepcopy(detail) if detail is not None else None


def get_history_items() -> list[dict[str, Any]]:
    default_sample_fps = current_app.config["DEFAULT_SAMPLE_FPS"]
    items = [
        {
            "id": 1,
            "standard_video_id": 1,
            "learner_video_id": 2,
            "sample_fps": default_sample_fps,
            "status": "success",
            "score": 82.5,
            "summary_text": "整体动作接近标准视频，左肘和右膝偏差较明显。",
            "created_at": "2026-03-12 14:40:00",
            "updated_at": "2026-03-12 14:40:20",
        },
        {
            "id": 2,
            "standard_video_id": 2,
            "learner_video_id": 3,
            "sample_fps": default_sample_fps,
            "status": "failed",
            "score": None,
            "summary_text": "分析任务创建成功，但当前仍使用占位结果，等待真实能力接入。",
            "created_at": "2026-03-12 15:05:00",
            "updated_at": "2026-03-12 15:05:10",
        },
    ]
    return deepcopy(items)


def get_history_detail(history_id: int) -> dict[str, Any] | None:
    details = {
        1: {
            "joint_diffs": {
                "left_elbow": 18.2,
                "right_elbow": 10.4,
                "left_knee": 7.9,
                "right_knee": 16.8,
            },
            "trajectory_diffs": {
                "left_hand": 0.21,
                "right_hand": 0.13,
                "left_foot": 0.11,
                "right_foot": 0.18,
            },
            "suggestions": [
                "左肘弯曲不足",
                "右膝抬起幅度偏低",
            ],
        },
        2: {
            "joint_diffs": {},
            "trajectory_diffs": {},
            "suggestions": [
                "当前记录仍使用失败占位结果，尚未生成可展示的差异明细。",
            ],
        },
    }

    item = next(
        (record for record in get_history_items() if record["id"] == history_id),
        None,
    )
    if item is None:
        return None

    detail = deepcopy(item)
    detail.update(details.get(history_id, {}))
    return detail
