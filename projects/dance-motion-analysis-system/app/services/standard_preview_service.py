import json
from pathlib import Path
from typing import Any

from flask import current_app

from app.algorithms.angle_calculator import ANGLE_KEYS, TRAJECTORY_NODE_NAMES
from app.services.skeleton_mapper import map_landmarks_to_nodes_3d

from .template_service import build_motion_metrics, build_standard_template, load_json_payload
from .video_records_service import get_video_record


PREVIEW_LANDMARK_NAMES = (
    "left_wrist",
    "right_wrist",
    "left_index",
    "right_index",
    "left_pinky",
    "right_pinky",
    "left_thumb",
    "right_thumb",
    "left_ankle",
    "right_ankle",
    "left_heel",
    "right_heel",
    "left_foot_index",
    "right_foot_index",
)


def _pick_frame(frames: list[dict], index: int) -> dict[str, Any] | None:
    if not frames:
        return None
    return frames[index]


def _pick_preview_landmarks(keypoint_frame: dict[str, Any] | None) -> dict[str, dict[str, Any]]:
    if keypoint_frame is None:
        return {}

    landmarks = keypoint_frame.get("landmarks", {})
    return {
        name: landmarks[name]
        for name in PREVIEW_LANDMARK_NAMES
        if name in landmarks
    }


def _build_frame_snapshot(
    keypoint_frame: dict[str, Any] | None,
    skeleton_frame: dict[str, Any] | None,
    angle_frame: dict[str, Any] | None,
    normalized_frame: dict[str, Any] | None,
) -> dict[str, Any] | None:
    if keypoint_frame is None or skeleton_frame is None or angle_frame is None or normalized_frame is None:
        return None

    return {
        "frame_index": skeleton_frame["frame_index"],
        "timestamp_ms": skeleton_frame["timestamp_ms"],
        "landmark_count": len(keypoint_frame.get("landmarks", {})),
        "node_count": len(skeleton_frame.get("nodes", {})),
        "angle_count": len([value for value in angle_frame.get("angles", {}).values() if value is not None]),
        "nodes": skeleton_frame.get("nodes", {}),
        "nodes_3d": map_landmarks_to_nodes_3d(keypoint_frame.get("landmarks", {})),
        "angles": angle_frame.get("angles", {}),
        "normalized_nodes": normalized_frame.get("nodes", {}),
        "landmarks": _pick_preview_landmarks(keypoint_frame),
    }


def _build_preview_output_paths(video_id: int, sample_fps: int, video_type: str) -> tuple[Path, Path, str, str]:
    base_name = f"{video_type}_video_{video_id}_{sample_fps}fps"
    angles_relative = Path("storage") / "outputs" / "previews" / f"{base_name}_angles.json"
    normalized_relative = Path("storage") / "outputs" / "previews" / f"{base_name}_norm.json"
    angles_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "previews" / angles_relative.name
    normalized_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "previews" / normalized_relative.name
    return (
        angles_absolute,
        normalized_absolute,
        angles_relative.as_posix(),
        normalized_relative.as_posix(),
    )


def _write_preview_metrics(
    video_id: int,
    sample_fps: int,
    video_type: str,
    angle_frames: list[dict[str, Any]],
    normalized_frames: list[dict[str, Any]],
) -> tuple[str, str]:
    (
        angles_absolute,
        normalized_absolute,
        angles_relative,
        normalized_relative,
    ) = _build_preview_output_paths(video_id, sample_fps, video_type)

    angles_payload = {
        "video_id": video_id,
        "video_type": video_type,
        "sample_fps": sample_fps,
        "frames": angle_frames,
    }
    normalized_payload = {
        "video_id": video_id,
        "video_type": video_type,
        "sample_fps": sample_fps,
        "frames": normalized_frames,
    }

    angles_absolute.parent.mkdir(parents=True, exist_ok=True)
    normalized_absolute.parent.mkdir(parents=True, exist_ok=True)
    angles_absolute.write_text(
        json.dumps(angles_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    normalized_absolute.write_text(
        json.dumps(normalized_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return angles_relative, normalized_relative


def _assemble_preview(
    video: dict[str, Any],
    sample_fps: int,
    files: dict[str, str],
) -> dict[str, Any]:
    keypoints_payload = load_json_payload(files["keypoints_path"])
    skeleton_payload = load_json_payload(files["skeleton_path"])
    angles_payload = load_json_payload(files["angles_path"])
    normalized_payload = load_json_payload(files["normalized_data_path"])

    keypoint_frames = keypoints_payload.get("frames", [])
    skeleton_frames = skeleton_payload.get("frames", [])
    angle_frames = angles_payload.get("frames", [])
    normalized_frames = normalized_payload.get("frames", [])

    first_frame = _build_frame_snapshot(
        _pick_frame(keypoint_frames, 0),
        _pick_frame(skeleton_frames, 0),
        _pick_frame(angle_frames, 0),
        _pick_frame(normalized_frames, 0),
    )
    last_frame = _build_frame_snapshot(
        _pick_frame(keypoint_frames, -1),
        _pick_frame(skeleton_frames, -1),
        _pick_frame(angle_frames, -1),
        _pick_frame(normalized_frames, -1),
    )

    return {
        "video": video,
        "sample_fps": sample_fps,
        "frame_count": len(skeleton_frames),
        "duration_ms": skeleton_frames[-1]["timestamp_ms"] if skeleton_frames else 0,
        "tracked_joints": list(ANGLE_KEYS),
        "tracked_trajectories": list(TRAJECTORY_NODE_NAMES),
        "display_node_names": keypoints_payload.get("display_node_names", []),
        "landmark_names": keypoints_payload.get("landmark_names", []),
        "files": files,
        "frames": [
            {
                "frame_index": skeleton_frame["frame_index"],
                "timestamp_ms": skeleton_frame["timestamp_ms"],
                "nodes": skeleton_frame.get("nodes", {}),
                "nodes_3d": map_landmarks_to_nodes_3d(keypoint_frame.get("landmarks", {})),
                "landmarks": _pick_preview_landmarks(keypoint_frame),
            }
            for skeleton_frame, keypoint_frame in zip(skeleton_frames, keypoint_frames)
        ],
        "first_frame": first_frame,
        "last_frame": last_frame,
    }


def build_standard_preview(video_id: int, sample_fps: int) -> dict[str, Any]:
    video = get_video_record(video_id)
    if video is None or video["video_type"] != "standard":
        raise LookupError("invalid standard video id")

    template_manifest = build_standard_template(video_id, sample_fps)
    return _assemble_preview(
        video,
        sample_fps,
        {
            "keypoints_path": template_manifest["keypoints_path"],
            "skeleton_path": template_manifest["skeleton_path"],
            "template_path": template_manifest["template_path"],
            "angles_path": template_manifest["angles_path"],
            "normalized_data_path": template_manifest["normalized_data_path"],
        },
    )


def build_learner_preview(video_id: int, sample_fps: int) -> dict[str, Any]:
    video = get_video_record(video_id)
    if video is None or video["video_type"] != "learner":
        raise LookupError("invalid learner video id")

    metrics = build_motion_metrics(video_id, sample_fps)
    pose_outputs = metrics["pose_outputs"]
    angles_path, normalized_data_path = _write_preview_metrics(
        video_id,
        sample_fps,
        "learner",
        metrics["angle_frames"],
        metrics["normalized_frames"],
    )
    return _assemble_preview(
        video,
        sample_fps,
        {
            "keypoints_path": pose_outputs["keypoints_path"],
            "skeleton_path": pose_outputs["skeleton_path"],
            "angles_path": angles_path,
            "normalized_data_path": normalized_data_path,
        },
    )
