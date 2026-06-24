import json
from datetime import datetime
from pathlib import Path
from typing import Any

from flask import current_app

from .compare_service import SCORE_CALIBRATION_VERSION, calculate_motion_score, compare_analysis_videos
from .feedback_rule_service import generate_feedback
from .json_store import read_records, update_records
from .pose_service import extract_pose_outputs
from .video_records_service import get_video_record


def _now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _round_float(value: float) -> float:
    return round(float(value), 4)


def _parse_record_time(value: str | None) -> datetime:
    if not value:
        return datetime.min
    try:
        return datetime.strptime(value, "%Y-%m-%d %H:%M:%S")
    except ValueError:
        return datetime.min


def list_analysis_records(include_review_payload: bool = False, saved_only: bool = False) -> list[dict[str, Any]]:
    records = read_records(current_app.config["ANALYSIS_RECORDS_FILE"])
    if saved_only:
        records = [record for record in records if record.get("is_saved")]
    sorted_records = sorted(
        records,
        key=lambda record: (
            _parse_record_time(record.get("updated_at")),
            _parse_record_time(record.get("created_at")),
            int(record.get("id", 0)),
        ),
        reverse=True,
    )
    if not include_review_payload:
        return sorted_records
    return [
        _merge_record_with_review_payload(record)
        for record in sorted_records
    ]


def get_analysis_record(analysis_id: int) -> dict[str, Any] | None:
    return next(
        (
            record
            for record in list_analysis_records()
            if record["id"] == analysis_id
        ),
        None,
    )


def _result_absolute_path(relative_path: str) -> Path:
    return Path(current_app.config["STORAGE_ROOT"]).parent / relative_path


def _build_result_paths(analysis_id: int) -> tuple[Path, str]:
    result_relative = Path("storage") / "outputs" / "comparisons" / f"analysis_{analysis_id}.json"
    result_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "comparisons" / result_relative.name
    return result_absolute, result_relative.as_posix()


def _load_result_payload(result_json_path: str | None) -> dict[str, Any]:
    if not result_json_path:
        return {}

    absolute_path = _result_absolute_path(result_json_path)
    if not absolute_path.exists():
        return {}

    payload = json.loads(absolute_path.read_text(encoding="utf-8"))
    return _calibrate_result_payload(payload)


def _calibrate_result_payload(payload: dict[str, Any]) -> dict[str, Any]:
    joint_diffs = payload.get("joint_diffs")
    trajectory_diffs = payload.get("trajectory_diffs")
    if not isinstance(joint_diffs, dict) or not isinstance(trajectory_diffs, dict):
        return payload

    calibrated_score = calculate_motion_score(joint_diffs, trajectory_diffs)
    calibrated_payload = dict(payload)
    calibrated_payload["score"] = calibrated_score
    calibrated_payload["score_version"] = SCORE_CALIBRATION_VERSION
    calibrated_payload.update(
        generate_feedback(
            joint_diffs,
            trajectory_diffs,
            calibrated_score,
            issues=payload.get("issues"),
            issue_segments=payload.get("issue_segments"),
        )
    )
    return calibrated_payload


def _load_result_review_payload(result_json_path: str | None) -> dict[str, Any]:
    payload = _load_result_payload(result_json_path)
    if not payload:
        return {}

    review_keys = (
        "score",
        "score_version",
        "summary_text",
        "joint_diffs",
        "trajectory_diffs",
        "issues",
        "suggestions",
        "structured_suggestions",
    )
    return {
        key: payload[key]
        for key in review_keys
        if key in payload
    }


def _merge_record_with_result(record: dict[str, Any]) -> dict[str, Any]:
    merged = dict(record)
    merged.update(_load_result_payload(record.get("result_json_path")))
    return merged


def _merge_record_with_review_payload(record: dict[str, Any]) -> dict[str, Any]:
    merged = dict(record)
    merged.update(_load_result_review_payload(record.get("result_json_path")))
    return merged


def _update_analysis_record(analysis_id: int, updates: dict[str, Any]) -> dict[str, Any]:
    def updater(records: list[dict[str, Any]]) -> dict[str, Any]:
        for record in records:
            if record["id"] == analysis_id:
                record.update(updates)
                return dict(record)
        raise LookupError("analysis not found")

    return update_records(current_app.config["ANALYSIS_RECORDS_FILE"], updater)


def create_analysis_record(
    standard_video_id: int,
    learner_video_id: int,
    sample_fps: int,
) -> dict[str, Any]:
    standard_video = get_video_record(standard_video_id)
    learner_video = get_video_record(learner_video_id)

    if standard_video is None or standard_video["video_type"] != "standard":
        raise LookupError("invalid standard video id")
    if learner_video is None or learner_video["video_type"] != "learner":
        raise LookupError("invalid learner video id")
    if sample_fps not in current_app.config["ALLOWED_SAMPLE_FPS"]:
        raise ValueError("invalid sample fps")

    def updater(records: list[dict[str, Any]]) -> dict[str, Any]:
        next_id = max((record["id"] for record in records), default=0) + 1
        now_text = _now_text()
        record = {
            "id": next_id,
            "standard_video_id": standard_video_id,
            "learner_video_id": learner_video_id,
            "standard_video_label": standard_video.get("display_name") or standard_video.get("original_filename") or f"参考视频 #{standard_video_id}",
            "learner_video_label": learner_video.get("display_name") or learner_video.get("original_filename") or f"待评分视频 #{learner_video_id}",
            "sample_fps": sample_fps,
            "status": "pending",
            "score": None,
            "result_json_path": None,
            "summary_text": None,
            "is_saved": False,
            "saved_at": None,
            "created_at": now_text,
            "updated_at": now_text,
        }
        records.append(record)
        return dict(record)

    return update_records(current_app.config["ANALYSIS_RECORDS_FILE"], updater)


def prepare_analysis_pose_inputs(analysis_id: int) -> dict[str, Any]:
    analysis = get_analysis_record(analysis_id)
    if analysis is None:
        raise LookupError("analysis not found")

    sample_fps = int(analysis["sample_fps"])
    return {
        "analysis_id": analysis_id,
        "sample_fps": sample_fps,
        "standard_video": extract_pose_outputs(
            int(analysis["standard_video_id"]),
            sample_fps,
        ),
        "learner_video": extract_pose_outputs(
            int(analysis["learner_video_id"]),
            sample_fps,
        ),
    }


def run_analysis_record(analysis_id: int) -> dict[str, Any]:
    analysis = get_analysis_record(analysis_id)
    if analysis is None:
        raise LookupError("analysis not found")

    existing_result = _load_result_payload(analysis.get("result_json_path"))
    if analysis.get("status") == "success" and existing_result:
        return _merge_record_with_result(analysis)

    _update_analysis_record(
        analysis_id,
        {
            "status": "running",
            "updated_at": _now_text(),
        },
    )

    try:
        comparison_result = compare_analysis_videos(
            analysis_id=analysis_id,
            standard_video_id=int(analysis["standard_video_id"]),
            learner_video_id=int(analysis["learner_video_id"]),
            sample_fps=int(analysis["sample_fps"]),
        )
        feedback = generate_feedback(
            comparison_result["joint_diffs"],
            comparison_result["trajectory_diffs"],
            comparison_result["score"],
            issues=comparison_result.get("issues"),
            issue_segments=comparison_result.get("issue_segments"),
        )
        result_payload = {
            **comparison_result,
            **feedback,
        }

        result_absolute, result_relative = _build_result_paths(analysis_id)
        result_absolute.parent.mkdir(parents=True, exist_ok=True)
        result_absolute.write_text(
            json.dumps(result_payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        _update_analysis_record(
            analysis_id,
            {
                "status": "success",
                "score": _round_float(result_payload["score"]),
                "result_json_path": result_relative,
                "summary_text": result_payload["summary_text"],
                "updated_at": _now_text(),
            },
        )
    except Exception as error:
        failed_record = _update_analysis_record(
            analysis_id,
            {
                "status": "failed",
                "score": None,
                "summary_text": f"analysis failed: {error}",
                "updated_at": _now_text(),
            },
        )
        return failed_record

    return get_analysis_detail_record(analysis_id) or {}


def save_analysis_record(analysis_id: int) -> dict[str, Any]:
    analysis = get_analysis_detail_record(analysis_id, ensure_result=True)
    if analysis is None:
        raise LookupError("analysis not found")

    if analysis.get("status") != "success":
        raise ValueError("analysis not ready to save")

    if analysis.get("is_saved"):
        return analysis

    now_text = _now_text()
    _update_analysis_record(
        analysis_id,
        {
            "is_saved": True,
            "saved_at": now_text,
            "updated_at": now_text,
        },
    )
    return get_analysis_detail_record(analysis_id) or {}


def get_analysis_detail_record(analysis_id: int, ensure_result: bool = False) -> dict[str, Any] | None:
    analysis = get_analysis_record(analysis_id)
    if analysis is None:
        return None

    if ensure_result and analysis.get("status") != "failed":
        has_result = bool(_load_result_payload(analysis.get("result_json_path")))
        if not has_result:
            return run_analysis_record(analysis_id)

    return _merge_record_with_result(analysis)
