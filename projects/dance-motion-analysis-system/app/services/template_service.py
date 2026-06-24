import json
from math import sqrt
from pathlib import Path
from typing import Any

from flask import current_app

from app.algorithms.angle_calculator import ANGLE_KEYS, TRAJECTORY_NODE_NAMES, calculate_joint_angles
from app.algorithms.pose_schema import DISPLAY_NODE_NAMES

from .pose_service import extract_pose_outputs
from .video_records_service import get_video_record


def _round_float(value: float) -> float:
    return round(float(value), 4)


def _midpoint(a: dict, b: dict) -> dict[str, float]:
    return {
        "x": _round_float((a["x"] + b["x"]) / 2),
        "y": _round_float((a["y"] + b["y"]) / 2),
    }


def _distance(a: dict, b: dict) -> float:
    return sqrt((a["x"] - b["x"]) ** 2 + (a["y"] - b["y"]) ** 2)


def _normalize_point(point: dict, origin: dict, scale: float) -> dict[str, float]:
    return {
        "x": _round_float((point["x"] - origin["x"]) / scale),
        "y": _round_float((point["y"] - origin["y"]) / scale),
    }


def _build_template_paths(video_id: int, sample_fps: int) -> tuple[Path, Path, Path, str, str, str]:
    base_name = f"video_{video_id}_{sample_fps}fps"
    template_relative = Path("storage") / "outputs" / "templates" / f"{base_name}_template.json"
    angles_relative = Path("storage") / "outputs" / "templates" / f"{base_name}_angles.json"
    normalized_relative = Path("storage") / "outputs" / "templates" / f"{base_name}_norm.json"
    template_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "templates" / template_relative.name
    angles_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "templates" / angles_relative.name
    normalized_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "templates" / normalized_relative.name
    return (
        template_absolute,
        angles_absolute,
        normalized_absolute,
        template_relative.as_posix(),
        angles_relative.as_posix(),
        normalized_relative.as_posix(),
    )


def load_json_payload(relative_path: str) -> dict[str, Any]:
    absolute_path = Path(current_app.config["STORAGE_ROOT"]).parent / relative_path
    return json.loads(absolute_path.read_text(encoding="utf-8"))


def _normalize_nodes(nodes: dict[str, dict]) -> tuple[dict[str, float], float, dict[str, dict]]:
    required = ("left_shoulder", "right_shoulder", "left_hip", "right_hip")
    if not all(name in nodes for name in required):
        return {}, 0.0, {}

    hip_center = _midpoint(nodes["left_hip"], nodes["right_hip"])
    shoulder_center = _midpoint(nodes["left_shoulder"], nodes["right_shoulder"])
    scale = _distance(hip_center, shoulder_center)
    if scale == 0:
        return hip_center, 0.0, {}

    normalized_nodes = {}
    for name in DISPLAY_NODE_NAMES:
        node = nodes.get(name)
        if node is None:
            continue
        if name == "head_circle":
            normalized_nodes[name] = {
                "cx": _round_float((node["cx"] - hip_center["x"]) / scale),
                "cy": _round_float((node["cy"] - hip_center["y"]) / scale),
                "r": _round_float(node["r"] / scale),
            }
            continue
        normalized_nodes[name] = _normalize_point(node, hip_center, scale)

    return hip_center, scale, normalized_nodes


def build_motion_metrics(video_id: int, sample_fps: int) -> dict[str, Any]:
    pose_outputs = extract_pose_outputs(video_id, sample_fps)
    skeleton_payload = load_json_payload(pose_outputs["skeleton_path"])

    angle_frames = []
    normalized_frames = []
    for frame in skeleton_payload["frames"]:
        nodes = frame["nodes"]
        origin, scale, normalized_nodes = _normalize_nodes(nodes)
        angle_frames.append(
            {
                "frame_index": frame["frame_index"],
                "timestamp_ms": frame["timestamp_ms"],
                "angles": calculate_joint_angles(nodes),
            }
        )
        normalized_frames.append(
            {
                "frame_index": frame["frame_index"],
                "timestamp_ms": frame["timestamp_ms"],
                "origin": origin,
                "scale": _round_float(scale),
                "nodes": normalized_nodes,
            }
        )

    return {
        "pose_outputs": pose_outputs,
        "angle_frames": angle_frames,
        "normalized_frames": normalized_frames,
    }


def build_standard_template(standard_video_id: int, sample_fps: int) -> dict[str, Any]:
    video = get_video_record(standard_video_id)
    if video is None or video["video_type"] != "standard":
        raise LookupError("invalid standard video id")

    (
        template_absolute,
        angles_absolute,
        normalized_absolute,
        template_relative,
        angles_relative,
        normalized_relative,
    ) = _build_template_paths(standard_video_id, sample_fps)
    if template_absolute.exists() and angles_absolute.exists() and normalized_absolute.exists():
        return load_json_payload(template_relative)

    metrics = build_motion_metrics(standard_video_id, sample_fps)
    pose_outputs = metrics["pose_outputs"]
    angle_frames = metrics["angle_frames"]
    normalized_frames = metrics["normalized_frames"]

    angles_payload = {
        "video_id": standard_video_id,
        "sample_fps": sample_fps,
        "frames": angle_frames,
    }
    normalized_payload = {
        "video_id": standard_video_id,
        "sample_fps": sample_fps,
        "frames": normalized_frames,
    }
    template_payload = {
        "standard_video_id": standard_video_id,
        "sample_fps": sample_fps,
        "frame_count": len(angle_frames),
        "template_path": template_relative,
        "keypoints_path": pose_outputs["keypoints_path"],
        "skeleton_path": pose_outputs["skeleton_path"],
        "angles_path": angles_relative,
        "normalized_data_path": normalized_relative,
        "tracked_joints": ANGLE_KEYS,
        "tracked_trajectories": TRAJECTORY_NODE_NAMES,
    }

    angles_absolute.parent.mkdir(parents=True, exist_ok=True)
    normalized_absolute.parent.mkdir(parents=True, exist_ok=True)
    template_absolute.parent.mkdir(parents=True, exist_ok=True)
    angles_absolute.write_text(
        json.dumps(angles_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    normalized_absolute.write_text(
        json.dumps(normalized_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    template_absolute.write_text(
        json.dumps(template_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    return template_payload
