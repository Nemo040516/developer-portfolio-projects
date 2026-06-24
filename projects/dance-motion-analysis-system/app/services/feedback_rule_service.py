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


def _issue_label(issue_type: str, issue_key: str) -> str:
    if issue_type == "joint":
        return JOINT_LABELS.get(issue_key, issue_key)
    return TRAJECTORY_LABELS.get(issue_key, issue_key)


def _top_issue_items(
    joint_diffs: dict[str, float],
    trajectory_diffs: dict[str, float],
) -> list[dict[str, object]]:
    issues = []
    for key, value in joint_diffs.items():
        issues.append(
            {
                "issue_type": "joint",
                "issue_key": key,
                "issue_label": _issue_label("joint", key),
                "average_diff": float(value),
                "peak_diff": float(value),
                "target_region": "",
                "segment_id": None,
            }
        )
    for key, value in trajectory_diffs.items():
        issues.append(
            {
                "issue_type": "trajectory",
                "issue_key": key,
                "issue_label": _issue_label("trajectory", key),
                "average_diff": float(value),
                "peak_diff": float(value),
                "target_region": "",
                "segment_id": None,
            }
        )
    return sorted(issues, key=lambda item: float(item["average_diff"]), reverse=True)


def _joint_suggestion(joint_name: str) -> str:
    label = JOINT_LABELS[joint_name]
    if "elbow" in joint_name:
        return f"{label}弯曲控制偏差较大，建议对照标准视频稳定手臂收放幅度。"
    return f"{label}伸展幅度偏差较大，建议重点校正下肢发力与屈伸节奏。"


def _trajectory_suggestion(node_name: str) -> str:
    label = TRAJECTORY_LABELS[node_name]
    if "hand" in node_name:
        return f"{label}偏移较明显，建议收紧上肢动作路径，减少摆动外扩。"
    return f"{label}偏移较明显，建议保持步幅稳定，避免落点左右漂移。"


def _build_suggestion_text(issue_type: str, issue_key: str) -> str:
    if issue_type == "joint":
        return _joint_suggestion(issue_key)
    return _trajectory_suggestion(issue_key)


def _build_segment_label(segment: dict[str, object] | None) -> str:
    if not segment:
        return "当前暂无可定位片段"

    start_seconds = float(segment.get("start_timestamp_ms") or 0) / 1000
    end_seconds = float(segment.get("end_timestamp_ms") or 0) / 1000
    center_seconds = float(segment.get("center_timestamp_ms") or 0) / 1000
    standard_start_seconds = float(segment.get("standard_start_timestamp_ms") or segment.get("start_timestamp_ms") or 0) / 1000
    standard_end_seconds = float(segment.get("standard_end_timestamp_ms") or segment.get("end_timestamp_ms") or 0) / 1000
    standard_center_seconds = float(segment.get("standard_center_timestamp_ms") or segment.get("center_timestamp_ms") or 0) / 1000
    learner_start_seconds = float(segment.get("learner_start_timestamp_ms") or segment.get("start_timestamp_ms") or 0) / 1000
    learner_end_seconds = float(segment.get("learner_end_timestamp_ms") or segment.get("end_timestamp_ms") or 0) / 1000
    learner_center_seconds = float(segment.get("learner_center_timestamp_ms") or segment.get("center_timestamp_ms") or 0) / 1000

    if (
        abs(standard_center_seconds - learner_center_seconds) >= 0.25
        or abs(standard_start_seconds - learner_start_seconds) >= 0.25
        or abs(standard_end_seconds - learner_end_seconds) >= 0.25
    ):
        if (
            abs(standard_start_seconds - standard_end_seconds) < 0.05
            and abs(learner_start_seconds - learner_end_seconds) < 0.05
        ):
            return f"示范 {standard_center_seconds:.2f}s / 练习 {learner_center_seconds:.2f}s"
        return (
            f"示范 {standard_start_seconds:.2f}s - {standard_end_seconds:.2f}s"
            f" / 练习 {learner_start_seconds:.2f}s - {learner_end_seconds:.2f}s"
        )

    if abs(start_seconds - end_seconds) < 0.05:
        return f"建议回看 {center_seconds:.2f}s 左右"
    return f"建议回看 {start_seconds:.2f}s - {end_seconds:.2f}s"


def _build_structured_suggestions(
    issues: list[dict[str, object]],
    issue_segments: list[dict[str, object]] | None,
) -> list[dict[str, object]]:
    if not issues:
        return []

    segment_map = {
        str(segment["segment_id"]): segment
        for segment in (issue_segments or [])
        if segment.get("segment_id")
    }
    structured = []
    for index, issue in enumerate(issues[:2], start=1):
        suggestion_text = _build_suggestion_text(str(issue["issue_type"]), str(issue["issue_key"]))
        segment = segment_map.get(str(issue.get("segment_id") or ""))
        structured_item = {
            "id": f"suggestion_{issue['issue_type']}_{issue['issue_key']}_{index}",
            "text": suggestion_text,
            "issue_type": issue["issue_type"],
            "issue_key": issue["issue_key"],
            "issue_label": issue.get("issue_label") or _issue_label(str(issue["issue_type"]), str(issue["issue_key"])),
            "target_region": issue.get("target_region") or "",
            "segment_id": issue.get("segment_id"),
            "segment_label": _build_segment_label(segment),
        }
        if segment:
            structured_item.update(
                {
                    "start_offset": segment["start_offset"],
                    "end_offset": segment["end_offset"],
                    "center_offset": segment["center_offset"],
                    "start_progress": segment["start_progress"],
                    "end_progress": segment["end_progress"],
                    "center_progress": segment["center_progress"],
                    "start_timestamp_ms": segment["start_timestamp_ms"],
                    "end_timestamp_ms": segment["end_timestamp_ms"],
                    "center_timestamp_ms": segment["center_timestamp_ms"],
                    "standard_start_timestamp_ms": segment.get("standard_start_timestamp_ms"),
                    "standard_end_timestamp_ms": segment.get("standard_end_timestamp_ms"),
                    "standard_center_timestamp_ms": segment.get("standard_center_timestamp_ms"),
                    "learner_start_timestamp_ms": segment.get("learner_start_timestamp_ms"),
                    "learner_end_timestamp_ms": segment.get("learner_end_timestamp_ms"),
                    "learner_center_timestamp_ms": segment.get("learner_center_timestamp_ms"),
                }
            )
        structured.append(structured_item)
    return structured


def generate_feedback(
    joint_diffs: dict[str, float],
    trajectory_diffs: dict[str, float],
    score: float,
    issues: list[dict[str, object]] | None = None,
    issue_segments: list[dict[str, object]] | None = None,
) -> dict[str, object]:
    ranked_issues = issues or _top_issue_items(joint_diffs, trajectory_diffs)
    if not ranked_issues or score >= 95:
        return {
            "summary_text": "整体动作与标准视频高度接近，当前未检测到明显偏差项。",
            "suggestions": [
                "整体节奏和动作幅度保持得较稳定，可以继续巩固当前完成度。",
                "后续可在保持稳定的前提下，继续细化动作连贯性和节奏一致性。",
            ],
            "structured_suggestions": [],
        }

    suggestions = []
    for issue in ranked_issues:
        suggestions.append(_build_suggestion_text(str(issue["issue_type"]), str(issue["issue_key"])))
        if len(suggestions) >= 2:
            break

    while len(suggestions) < 2:
        suggestions.append("整体动作完成度较高，建议继续保持当前节奏并细化动作稳定性。")

    focus_labels = [
        str(issue.get("issue_label") or _issue_label(str(issue["issue_type"]), str(issue["issue_key"])))
        for issue in ranked_issues[:2]
    ]

    if focus_labels:
        focus_text = "，主要偏差集中在" + "、".join(focus_labels) + "。"
    else:
        focus_text = "，当前未检测到明显偏差项。"

    if score >= 90:
        prefix = "整体动作与标准视频高度接近"
    elif score >= 70:
        prefix = "整体动作基本接近标准视频"
    elif score >= 60:
        prefix = "整体动作完成度一般"
    else:
        prefix = "整体动作与标准视频存在较明显差距"

    return {
        "summary_text": prefix + focus_text,
        "suggestions": suggestions,
        "structured_suggestions": _build_structured_suggestions(ranked_issues, issue_segments),
    }
