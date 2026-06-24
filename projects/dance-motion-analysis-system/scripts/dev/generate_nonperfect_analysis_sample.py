from __future__ import annotations

import argparse
import sys
import tempfile
from pathlib import Path

import cv2
from werkzeug.datastructures import FileStorage

PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app import create_app
from app.services.analysis_records_service import create_analysis_record, run_analysis_record
from app.services.video_records_service import create_video_record, get_video_record, resolve_video_file_path


PROFILE_ANCHORS = {
    "mild": (
        (0.0, 0.0),
        (0.45, 0.40),
        (0.68, 0.62),
        (1.0, 1.0),
    ),
    "medium": (
        (0.0, 0.0),
        (0.42, 0.26),
        (0.65, 0.44),
        (1.0, 1.0),
    ),
    "strong": (
        (0.0, 0.0),
        (0.40, 0.18),
        (0.62, 0.28),
        (1.0, 1.0),
    ),
}


def _interpolate_progress(progress: float, anchors: tuple[tuple[float, float], ...]) -> float:
    if progress <= anchors[0][0]:
        return anchors[0][1]

    for left_anchor, right_anchor in zip(anchors, anchors[1:]):
        left_progress, left_value = left_anchor
        right_progress, right_value = right_anchor
        if progress <= right_progress:
            span = right_progress - left_progress
            if span <= 0:
                return right_value
            ratio = (progress - left_progress) / span
            return left_value + ((right_value - left_value) * ratio)

    return anchors[-1][1]


def build_output_mapping(frame_count: int, profile: str) -> list[int]:
    if frame_count <= 1:
        return [0]

    anchors = PROFILE_ANCHORS[profile]
    mapped_indices = []
    last_index = frame_count - 1
    for output_index in range(frame_count):
        progress = output_index / last_index
        source_progress = _interpolate_progress(progress, anchors)
        mapped_indices.append(min(last_index, max(0, round(source_progress * last_index))))
    return mapped_indices


def build_temporal_warp_video(
    input_path: Path,
    output_path: Path,
    profile: str,
) -> dict[str, float | int | str]:
    capture = cv2.VideoCapture(str(input_path))
    if not capture.isOpened():
        raise RuntimeError(f"cannot open source video: {input_path}")

    frame_rate = float(capture.get(cv2.CAP_PROP_FPS) or 0.0) or 25.0
    frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    if frame_count <= 0 or width <= 0 or height <= 0:
        capture.release()
        raise RuntimeError("invalid source video metadata")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(str(output_path), fourcc, frame_rate, (width, height))
    if not writer.isOpened():
        capture.release()
        raise RuntimeError(f"cannot create output video: {output_path}")

    source_indices = build_output_mapping(frame_count, profile)
    current_output_index = 0
    current_source_index = 0
    last_frame = None

    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break

            last_frame = frame
            while (
                current_output_index < frame_count
                and source_indices[current_output_index] == current_source_index
            ):
                writer.write(frame)
                current_output_index += 1

            current_source_index += 1

        if last_frame is None:
            raise RuntimeError("source video has no readable frame")

        while current_output_index < frame_count:
            writer.write(last_frame)
            current_output_index += 1
    finally:
        capture.release()
        writer.release()

    return {
        "frame_rate": round(frame_rate, 2),
        "frame_count": frame_count,
        "width": width,
        "height": height,
        "profile": profile,
        "output_path": str(output_path),
    }


def import_learner_video(file_path: Path, filename: str) -> dict:
    with file_path.open("rb") as handle:
        storage = FileStorage(
            stream=handle,
            filename=filename,
            content_type="video/mp4",
        )
        return create_video_record(storage, "learner")


def run_sample_flow(
    standard_video_id: int,
    sample_fps: int,
    profile: str,
    filename: str,
) -> dict:
    standard_video = get_video_record(standard_video_id)
    if standard_video is None or standard_video["video_type"] != "standard":
        raise LookupError(f"invalid standard video id: {standard_video_id}")

    source_video_path = resolve_video_file_path(standard_video)
    if not source_video_path.exists():
        raise FileNotFoundError(f"source video not found: {source_video_path}")

    with tempfile.TemporaryDirectory(prefix="motion_nonperfect_sample_") as temp_dir:
        temp_output_path = Path(temp_dir) / filename
        generation_result = build_temporal_warp_video(source_video_path, temp_output_path, profile)
        learner_video = import_learner_video(temp_output_path, filename)

    analysis = create_analysis_record(
        standard_video_id=standard_video_id,
        learner_video_id=int(learner_video["id"]),
        sample_fps=sample_fps,
    )
    analysis_result = run_analysis_record(int(analysis["id"]))

    return {
        "standard_video_id": standard_video_id,
        "learner_video_id": int(learner_video["id"]),
        "learner_file_path": learner_video["file_path"],
        "learner_display_name": learner_video.get("display_name"),
        "analysis_id": int(analysis_result["id"]),
        "score": float(analysis_result["score"]) if analysis_result.get("score") is not None else None,
        "summary_text": analysis_result.get("summary_text"),
        "suggestions": analysis_result.get("suggestions", []),
        "generation": generation_result,
    }


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate a reproducible non-perfect learner sample and analysis record.",
    )
    parser.add_argument("--standard-video-id", type=int, default=1)
    parser.add_argument("--sample-fps", type=int, default=5)
    parser.add_argument(
        "--profile",
        choices=tuple(PROFILE_ANCHORS.keys()),
        default="strong",
    )
    parser.add_argument(
        "--filename",
        default="learner_demo_sample.mp4",
    )
    return parser


def main() -> int:
    parser = build_argument_parser()
    args = parser.parse_args()

    app = create_app()
    with app.app_context():
        result = run_sample_flow(
            standard_video_id=args.standard_video_id,
            sample_fps=args.sample_fps,
            profile=args.profile,
            filename=args.filename,
        )

    print("standard_video_id =", result["standard_video_id"])
    print("learner_video_id  =", result["learner_video_id"])
    print("learner_name      =", result["learner_display_name"])
    print("learner_file_path =", result["learner_file_path"])
    print("analysis_id       =", result["analysis_id"])
    print("score             =", result["score"])
    print("summary_text      =", result["summary_text"])
    print("suggestions       =", " | ".join(result["suggestions"]))
    print("profile           =", result["generation"]["profile"])
    print("frame_rate        =", result["generation"]["frame_rate"])
    print("frame_count       =", result["generation"]["frame_count"])

    if result["score"] is None or result["score"] >= 100.0:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
