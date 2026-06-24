import hashlib
from math import sqrt
from typing import Any

from app.algorithms.angle_calculator import ANGLE_KEYS, TRAJECTORY_NODE_NAMES

from .template_service import build_motion_metrics, build_standard_template, load_json_payload
from .video_records_service import get_video_record, resolve_video_file_path


JOINT_LABELS = {
    "left_elbow": "左肘",
    "right_elbow": "右肘",
    "left_knee": "左膝",
    "right_knee": "右膝",
}

TRAJECTORY_LABELS = {
    "left_hand": "左手轨迹",
    "right_hand": "右手轨迹",
    "left_foot": "左脚轨迹",
    "right_foot": "右脚轨迹",
}

TARGET_REGIONS = {
    "left_elbow": "left-arm",
    "right_elbow": "right-arm",
    "left_knee": "left-leg",
    "right_knee": "right-leg",
    "left_hand": "left-arm",
    "right_hand": "right-arm",
    "left_foot": "left-leg",
    "right_foot": "right-leg",
}

SCORE_JOINT_WEIGHT = 0.78
SCORE_JOINT_PENALTY_PER_DEGREE = 0.75
SCORE_TRAJECTORY_PENALTY_PER_UNIT = 70.0
SCORE_CALIBRATION_VERSION = "motion-score-v2"


def _round_float(value: float) -> float:
    return round(float(value), 4)


def _average(values: list[float]) -> float:
    if not values:
        return 0.0
    return _round_float(sum(values) / len(values))


def _average_node_distance(left_nodes: dict[str, Any], right_nodes: dict[str, Any]) -> float:
    distances = []
    shared_node_names = set(left_nodes.keys()) & set(right_nodes.keys())
    for node_name in shared_node_names:
        left_node = left_nodes.get(node_name)
        right_node = right_nodes.get(node_name)
        if (
            left_node is None
            or right_node is None
            or "x" not in left_node
            or "y" not in left_node
            or "x" not in right_node
            or "y" not in right_node
        ):
            continue
        distances.append(_point_distance(left_node, right_node) or 0.0)
    return _average(distances)


def _resolve_motion_start_index(normalized_frames: list[dict[str, Any]]) -> int:
    if len(normalized_frames) < 3:
        return 0

    baseline_nodes = normalized_frames[0].get("nodes") or {}
    if not baseline_nodes:
        return 0

    activity_scores = [0.0]
    previous_nodes = baseline_nodes
    for frame in normalized_frames[1:]:
        current_nodes = frame.get("nodes") or {}
        pose_shift = _average_node_distance(baseline_nodes, current_nodes)
        step_shift = _average_node_distance(previous_nodes, current_nodes)
        activity_scores.append(_round_float((pose_shift * 0.7) + (step_shift * 0.3)))
        previous_nodes = current_nodes

    peak_score = max(activity_scores[1:], default=0.0)
    if peak_score <= 0:
        return 0

    sorted_scores = sorted(activity_scores)
    quiet_sample_count = max(1, len(sorted_scores) // 3)
    quiet_mean = _average(sorted_scores[:quiet_sample_count])
    active_threshold = max(0.015, quiet_mean * 4.0, peak_score * 0.18)
    consecutive_required = 2 if len(activity_scores) >= 6 else 1

    streak = 0
    for index in range(1, len(activity_scores)):
        if activity_scores[index] >= active_threshold:
            streak += 1
            if streak >= consecutive_required:
                trim_index = max(0, index - streak)
                if trim_index <= 1:
                    return 0
                if len(normalized_frames) - trim_index < 3:
                    return 0
                return trim_index
        else:
            streak = 0
    return 0


def _trim_leading_inactive_frames(
    angle_frames: list[dict[str, Any]],
    normalized_frames: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    motion_start_index = _resolve_motion_start_index(normalized_frames)
    if motion_start_index <= 0:
        return angle_frames, normalized_frames

    safe_index = min(motion_start_index, len(angle_frames) - 1, len(normalized_frames) - 1)
    return angle_frames[safe_index:], normalized_frames[safe_index:]


def _align_frames(left_frames: list[dict], right_frames: list[dict]) -> list[tuple[dict, dict]]:
    if not left_frames or not right_frames:
        return []

    pair_count = min(len(left_frames), len(right_frames))
    if pair_count == 1:
        return [(left_frames[0], right_frames[0])]

    left_last = len(left_frames) - 1
    right_last = len(right_frames) - 1
    aligned_pairs = []
    for index in range(pair_count):
        progress = index / (pair_count - 1)
        left_index = round(progress * left_last)
        right_index = round(progress * right_last)
        aligned_pairs.append((left_frames[left_index], right_frames[right_index]))
    return aligned_pairs


def _point_distance(a: dict | None, b: dict | None) -> float | None:
    if a is None or b is None:
        return None
    return sqrt((a["x"] - b["x"]) ** 2 + (a["y"] - b["y"]) ** 2)


def _resolve_issue_label(issue_type: str, issue_key: str) -> str:
    if issue_type == "joint":
        return JOINT_LABELS.get(issue_key, issue_key)
    return TRAJECTORY_LABELS.get(issue_key, issue_key)


def _build_metric_entry(
    aligned_offset: int,
    aligned_count: int,
    standard_frame: dict,
    learner_frame: dict,
    diff_value: float,
) -> dict[str, Any]:
    if aligned_count <= 1:
        progress = 0.0
    else:
        progress = aligned_offset / (aligned_count - 1)
    standard_timestamp = int(standard_frame.get("timestamp_ms") or 0)
    learner_timestamp = int(learner_frame.get("timestamp_ms") or 0)
    return {
        "aligned_offset": aligned_offset,
        "progress": _round_float(progress),
        "standard_frame_index": int(standard_frame.get("frame_index") or 0),
        "learner_frame_index": int(learner_frame.get("frame_index") or 0),
        "standard_timestamp_ms": standard_timestamp,
        "learner_timestamp_ms": learner_timestamp,
        "timestamp_ms": int(round((standard_timestamp + learner_timestamp) / 2)),
        "diff": _round_float(diff_value),
    }


def _build_profile(
    issue_type: str,
    issue_key: str,
    values: list[float],
    frame_metrics: list[dict[str, Any]],
) -> dict[str, Any]:
    average_diff = _average(values)
    peak_diff = max(values, default=0.0)
    return {
        "issue_type": issue_type,
        "issue_key": issue_key,
        "issue_label": _resolve_issue_label(issue_type, issue_key),
        "target_region": TARGET_REGIONS.get(issue_key, ""),
        "average_diff": average_diff,
        "peak_diff": _round_float(peak_diff),
        "frame_metrics": frame_metrics,
    }


def _compute_joint_profiles(
    standard_angle_frames: list[dict],
    learner_angle_frames: list[dict],
) -> tuple[dict[str, float], list[dict[str, Any]]]:
    aligned_pairs = _align_frames(standard_angle_frames, learner_angle_frames)
    aligned_count = len(aligned_pairs)
    joint_diffs = {}
    issue_profiles = []
    for joint_name in ANGLE_KEYS:
        diffs = []
        frame_metrics = []
        for aligned_offset, (standard_frame, learner_frame) in enumerate(aligned_pairs):
            standard_value = standard_frame["angles"].get(joint_name)
            learner_value = learner_frame["angles"].get(joint_name)
            if standard_value is None or learner_value is None:
                continue
            diff_value = abs(float(standard_value) - float(learner_value))
            diffs.append(diff_value)
            frame_metrics.append(
                _build_metric_entry(
                    aligned_offset=aligned_offset,
                    aligned_count=aligned_count,
                    standard_frame=standard_frame,
                    learner_frame=learner_frame,
                    diff_value=diff_value,
                )
            )
        joint_diffs[joint_name] = _average(diffs)
        issue_profiles.append(_build_profile("joint", joint_name, diffs, frame_metrics))
    return joint_diffs, issue_profiles


def _compute_trajectory_profiles(
    standard_normalized_frames: list[dict],
    learner_normalized_frames: list[dict],
) -> tuple[dict[str, float], list[dict[str, Any]]]:
    aligned_pairs = _align_frames(standard_normalized_frames, learner_normalized_frames)
    aligned_count = len(aligned_pairs)
    trajectory_diffs = {}
    issue_profiles = []
    for node_name in TRAJECTORY_NODE_NAMES:
        diffs = []
        frame_metrics = []
        for aligned_offset, (standard_frame, learner_frame) in enumerate(aligned_pairs):
            distance = _point_distance(
                standard_frame["nodes"].get(node_name),
                learner_frame["nodes"].get(node_name),
            )
            if distance is None:
                continue
            diffs.append(distance)
            frame_metrics.append(
                _build_metric_entry(
                    aligned_offset=aligned_offset,
                    aligned_count=aligned_count,
                    standard_frame=standard_frame,
                    learner_frame=learner_frame,
                    diff_value=distance,
                )
            )
        trajectory_diffs[node_name] = _average(diffs)
        issue_profiles.append(_build_profile("trajectory", node_name, diffs, frame_metrics))
    return trajectory_diffs, issue_profiles


def _collect_contiguous_groups(frame_metrics: list[dict[str, Any]], threshold: float) -> list[list[dict[str, Any]]]:
    if not frame_metrics:
        return []

    groups = []
    current_group = []
    previous_offset = None
    for item in frame_metrics:
        if item["diff"] < threshold:
            if current_group:
                groups.append(current_group)
                current_group = []
            previous_offset = None
            continue

        if previous_offset is None or item["aligned_offset"] == previous_offset + 1:
            current_group.append(item)
        else:
            groups.append(current_group)
            current_group = [item]
        previous_offset = item["aligned_offset"]

    if current_group:
        groups.append(current_group)
    return groups


def _pick_best_group(frame_metrics: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not frame_metrics:
        return []

    values = [float(item["diff"]) for item in frame_metrics]
    peak_diff = max(values)
    mean_diff = _average(values)
    threshold = max(mean_diff, peak_diff * 0.72)
    groups = _collect_contiguous_groups(frame_metrics, threshold)
    if not groups:
        peak_item = max(frame_metrics, key=lambda item: float(item["diff"]))
        return [peak_item]

    return max(
        groups,
        key=lambda group: (
            sum(float(item["diff"]) for item in group),
            max(float(item["diff"]) for item in group),
            len(group),
        ),
    )


def _pad_group(
    frame_metrics: list[dict[str, Any]],
    selected_group: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    if not frame_metrics or not selected_group:
        return []

    offset_to_index = {
        item["aligned_offset"]: index
        for index, item in enumerate(frame_metrics)
    }
    start_index = offset_to_index[selected_group[0]["aligned_offset"]]
    end_index = offset_to_index[selected_group[-1]["aligned_offset"]]
    padded_start = max(0, start_index - 1)
    padded_end = min(len(frame_metrics) - 1, end_index + 1)
    return frame_metrics[padded_start:padded_end + 1]


def _build_issue_segment(profile: dict[str, Any]) -> dict[str, Any] | None:
    frame_metrics = profile["frame_metrics"]
    if not frame_metrics:
        return None

    selected_group = _pick_best_group(frame_metrics)
    padded_group = _pad_group(frame_metrics, selected_group)
    start_item = padded_group[0]
    end_item = padded_group[-1]
    peak_item = max(selected_group, key=lambda item: float(item["diff"]))
    segment_id = f"{profile['issue_type']}_{profile['issue_key']}_primary"
    return {
        "segment_id": segment_id,
        "issue_type": profile["issue_type"],
        "issue_key": profile["issue_key"],
        "issue_label": profile["issue_label"],
        "target_region": profile["target_region"],
        "start_offset": start_item["aligned_offset"],
        "end_offset": end_item["aligned_offset"],
        "center_offset": peak_item["aligned_offset"],
        "start_progress": start_item["progress"],
        "end_progress": end_item["progress"],
        "center_progress": peak_item["progress"],
        "start_timestamp_ms": start_item["timestamp_ms"],
        "end_timestamp_ms": end_item["timestamp_ms"],
        "center_timestamp_ms": peak_item["timestamp_ms"],
        "standard_start_timestamp_ms": start_item["standard_timestamp_ms"],
        "standard_end_timestamp_ms": end_item["standard_timestamp_ms"],
        "standard_center_timestamp_ms": peak_item["standard_timestamp_ms"],
        "learner_start_timestamp_ms": start_item["learner_timestamp_ms"],
        "learner_end_timestamp_ms": end_item["learner_timestamp_ms"],
        "learner_center_timestamp_ms": peak_item["learner_timestamp_ms"],
        "frame_span": (end_item["aligned_offset"] - start_item["aligned_offset"]) + 1,
        "average_diff": profile["average_diff"],
        "peak_diff": profile["peak_diff"],
    }


def _build_issue_summaries(issue_profiles: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    issue_segments = []
    issue_summaries = []
    for profile in issue_profiles:
        segment = _build_issue_segment(profile)
        if segment is not None:
            issue_segments.append(segment)
        issue_summaries.append(
            {
                "issue_type": profile["issue_type"],
                "issue_key": profile["issue_key"],
                "issue_label": profile["issue_label"],
                "target_region": profile["target_region"],
                "average_diff": profile["average_diff"],
                "peak_diff": profile["peak_diff"],
                "segment_id": segment["segment_id"] if segment else None,
            }
        )

    issue_summaries.sort(
        key=lambda item: (float(item["average_diff"]), float(item["peak_diff"])),
        reverse=True,
    )
    return issue_summaries, issue_segments


def calculate_motion_score(joint_diffs: dict[str, float], trajectory_diffs: dict[str, float]) -> float:
    mean_joint_diff = _average(list(joint_diffs.values()))
    mean_trajectory_diff = _average(list(trajectory_diffs.values()))
    joint_score = max(0.0, 100.0 - (mean_joint_diff * SCORE_JOINT_PENALTY_PER_DEGREE))
    trajectory_score = max(0.0, 100.0 - (mean_trajectory_diff * SCORE_TRAJECTORY_PENALTY_PER_UNIT))
    trajectory_weight = 1.0 - SCORE_JOINT_WEIGHT
    return round((joint_score * SCORE_JOINT_WEIGHT) + (trajectory_score * trajectory_weight), 1)


def _calculate_score(joint_diffs: dict[str, float], trajectory_diffs: dict[str, float]) -> float:
    return calculate_motion_score(joint_diffs, trajectory_diffs)


def _compute_file_sha256(file_path) -> str:
    digest = hashlib.sha256()
    with file_path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(1024 * 1024), b""):
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def _resolve_content_hash(video: dict[str, Any] | None) -> str:
    if not video:
        return ""

    content_hash = str(video.get("content_hash") or "").strip()
    if content_hash:
        return content_hash

    file_path = video.get("file_path")
    if not file_path:
        return ""

    try:
        absolute_path = resolve_video_file_path(video)
    except KeyError:
        return ""

    if not absolute_path.exists():
        return ""

    return _compute_file_sha256(absolute_path)


def _is_same_source_video(standard_video_id: int, learner_video_id: int) -> bool:
    standard_video = get_video_record(standard_video_id)
    learner_video = get_video_record(learner_video_id)
    if not standard_video or not learner_video:
        return False
    standard_hash = _resolve_content_hash(standard_video)
    learner_hash = _resolve_content_hash(learner_video)
    return bool(standard_hash and learner_hash and standard_hash == learner_hash)


def _build_perfect_match_result(
    analysis_id: int,
    standard_video_id: int,
    learner_video_id: int,
    sample_fps: int,
) -> dict[str, Any]:
    return {
        "analysis_id": analysis_id,
        "standard_video_id": standard_video_id,
        "learner_video_id": learner_video_id,
        "sample_fps": sample_fps,
        "score": 100.0,
        "score_version": SCORE_CALIBRATION_VERSION,
        "joint_diffs": {
            joint_name: 0.0
            for joint_name in ANGLE_KEYS
        },
        "trajectory_diffs": {
            node_name: 0.0
            for node_name in TRAJECTORY_NODE_NAMES
        },
        "issues": [],
        "issue_segments": [],
    }


def compare_analysis_videos(
    analysis_id: int,
    standard_video_id: int,
    learner_video_id: int,
    sample_fps: int,
) -> dict[str, Any]:
    if _is_same_source_video(standard_video_id, learner_video_id):
        return _build_perfect_match_result(
            analysis_id=analysis_id,
            standard_video_id=standard_video_id,
            learner_video_id=learner_video_id,
            sample_fps=sample_fps,
        )

    template_manifest = build_standard_template(standard_video_id, sample_fps)
    standard_angles_payload = load_json_payload(template_manifest["angles_path"])
    standard_normalized_payload = load_json_payload(template_manifest["normalized_data_path"])
    learner_metrics = build_motion_metrics(learner_video_id, sample_fps)

    standard_angle_frames, standard_normalized_frames = _trim_leading_inactive_frames(
        standard_angles_payload["frames"],
        standard_normalized_payload["frames"],
    )
    learner_angle_frames, learner_normalized_frames = _trim_leading_inactive_frames(
        learner_metrics["angle_frames"],
        learner_metrics["normalized_frames"],
    )

    joint_diffs, joint_profiles = _compute_joint_profiles(
        standard_angle_frames,
        learner_angle_frames,
    )
    trajectory_diffs, trajectory_profiles = _compute_trajectory_profiles(
        standard_normalized_frames,
        learner_normalized_frames,
    )
    issues, issue_segments = _build_issue_summaries(joint_profiles + trajectory_profiles)

    return {
        "analysis_id": analysis_id,
        "standard_video_id": standard_video_id,
        "learner_video_id": learner_video_id,
        "sample_fps": sample_fps,
        "score": _calculate_score(joint_diffs, trajectory_diffs),
        "score_version": SCORE_CALIBRATION_VERSION,
        "joint_diffs": joint_diffs,
        "trajectory_diffs": trajectory_diffs,
        "issues": issues,
        "issue_segments": issue_segments,
    }
