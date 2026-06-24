import math
from pathlib import Path

import cv2
from flask import current_app

from .video_records_service import resolve_video_file_path


BROWSER_SOURCE_EXTENSION = ".webm"
BROWSER_SOURCE_CODEC = "VP80"
DEFAULT_BROWSER_SOURCE_FPS = 25.0


def _build_browser_source_relative_path(video: dict) -> Path:
    stored_filename = video.get("stored_filename") or f"video_{video.get('id', 'unknown')}.mp4"
    video_type = str(video.get("video_type") or "unknown")
    return (
        Path("storage")
        / "outputs"
        / "browser_sources"
        / video_type
        / f"{Path(stored_filename).stem}_browser{BROWSER_SOURCE_EXTENSION}"
    )


def resolve_browser_source_file_path(video: dict) -> Path:
    relative_path = _build_browser_source_relative_path(video)
    return current_app.config["STORAGE_ROOT"].parent / relative_path


def resolve_browser_source_relative_path(video: dict) -> str:
    return _build_browser_source_relative_path(video).as_posix()


def _resolve_output_size(video: dict, capture: cv2.VideoCapture) -> tuple[int, int]:
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH) or 0) or int(video.get("width") or 0)
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0) or int(video.get("height") or 0)
    if width <= 0 or height <= 0:
        raise RuntimeError("invalid video size")
    return width, height


def _resolve_output_fps(video: dict, capture: cv2.VideoCapture) -> float:
    fps = float(capture.get(cv2.CAP_PROP_FPS) or 0) or float(video.get("frame_rate") or 0) or DEFAULT_BROWSER_SOURCE_FPS
    if not math.isfinite(fps) or fps <= 1.0:
        return DEFAULT_BROWSER_SOURCE_FPS
    return fps


def _write_browser_source(video: dict, source_path: Path, output_path: Path) -> None:
    capture = cv2.VideoCapture(str(source_path))
    if not capture.isOpened():
        raise RuntimeError("unable to open source video")

    writer = None
    frames_written = 0
    try:
        width, height = _resolve_output_size(video, capture)
        fps = _resolve_output_fps(video, capture)
        writer = cv2.VideoWriter(
            str(output_path),
            cv2.VideoWriter_fourcc(*BROWSER_SOURCE_CODEC),
            fps,
            (width, height),
        )
        if not writer.isOpened():
            raise RuntimeError("unable to create browser source writer")

        while True:
            success, frame = capture.read()
            if not success or frame is None:
                break
            if frame.shape[1] != width or frame.shape[0] != height:
                frame = cv2.resize(frame, (width, height))
            writer.write(frame)
            frames_written += 1
    finally:
        capture.release()
        if writer is not None:
            writer.release()

    if frames_written <= 0:
        output_path.unlink(missing_ok=True)
        raise RuntimeError("browser source writer produced no frames")

    if not output_path.exists() or output_path.stat().st_size <= 0:
        output_path.unlink(missing_ok=True)
        raise RuntimeError("browser source output file missing")


def ensure_browser_video_source(video: dict) -> Path:
    source_path = resolve_video_file_path(video)
    if not source_path.exists():
        raise FileNotFoundError(source_path)

    output_path = resolve_browser_source_file_path(video)
    if output_path.exists() and output_path.stat().st_size > 0:
        return output_path

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.unlink(missing_ok=True)
    temp_output_path = output_path.with_name(output_path.stem + ".tmp" + output_path.suffix)
    temp_output_path.unlink(missing_ok=True)

    try:
        _write_browser_source(video, source_path, temp_output_path)
        temp_output_path.replace(output_path)
    except Exception:
        temp_output_path.unlink(missing_ok=True)
        output_path.unlink(missing_ok=True)
        raise

    return output_path
