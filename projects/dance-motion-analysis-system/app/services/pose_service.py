import json
from pathlib import Path
from typing import Any
from urllib.request import Request, urlopen

import cv2
import mediapipe as mp
from flask import current_app

from app.algorithms.pose_schema import DISPLAY_NODE_NAMES, LANDMARK_NAMES

from .skeleton_mapper import map_landmarks_to_nodes
from .video_records_service import get_video_record


def _round_float(value: float) -> float:
    return round(float(value), 4)


def _build_output_paths(video_id: int, sample_fps: int) -> tuple[Path, Path, str, str]:
    keypoints_relative = Path("storage") / "outputs" / "keypoints" / f"video_{video_id}_{sample_fps}fps.json"
    skeleton_relative = Path("storage") / "outputs" / "templates" / f"video_{video_id}_{sample_fps}fps_skeleton.json"
    keypoints_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "keypoints" / keypoints_relative.name
    skeleton_absolute = current_app.config["STORAGE_ROOT"] / "outputs" / "templates" / skeleton_relative.name
    return (
        keypoints_absolute,
        skeleton_absolute,
        keypoints_relative.as_posix(),
        skeleton_relative.as_posix(),
    )


def _sample_interval(video_fps: float, sample_fps: int) -> int:
    if video_fps <= 0:
        return 1
    return max(1, round(video_fps / sample_fps))


def _ensure_pose_landmarker_model() -> Path:
    model_path = Path(current_app.config["POSE_LANDMARKER_MODEL_PATH"])
    if model_path.exists():
        return model_path

    model_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = model_path.with_suffix(f"{model_path.suffix}.tmp")
    request = Request(
        current_app.config["POSE_LANDMARKER_MODEL_URL"],
        headers={"User-Agent": "Mozilla/5.0"},
    )
    with urlopen(request) as response:
        temp_path.write_bytes(response.read())
    temp_path.replace(model_path)
    return model_path


def _build_landmarker():
    base_options = mp.tasks.BaseOptions(
        model_asset_path=str(_ensure_pose_landmarker_model()),
    )
    options = mp.tasks.vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=mp.tasks.vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    return mp.tasks.vision.PoseLandmarker.create_from_options(options)


def extract_pose_outputs(video_id: int, sample_fps: int) -> dict[str, Any]:
    if sample_fps not in current_app.config["ALLOWED_SAMPLE_FPS"]:
        raise ValueError("invalid sample fps")

    video = get_video_record(video_id)
    if video is None:
        raise LookupError("video not found")

    absolute_video_path = Path(current_app.config["STORAGE_ROOT"]).parent / video["file_path"]
    if not absolute_video_path.exists():
        raise FileNotFoundError("video file not found")

    capture = cv2.VideoCapture(str(absolute_video_path))
    if not capture.isOpened():
        raise ValueError("invalid video file")

    video_fps = float(capture.get(cv2.CAP_PROP_FPS) or 0.0)
    interval = _sample_interval(video_fps, sample_fps)

    keypoint_frames = []
    skeleton_frames = []
    frame_index = 0

    try:
        with _build_landmarker() as landmarker:
            while True:
                ok, frame = capture.read()
                if not ok:
                    break

                if frame_index % interval != 0:
                    frame_index += 1
                    continue

                timestamp_ms = int(round((frame_index / video_fps) * 1000)) if video_fps > 0 else 0
                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(
                    image_format=mp.ImageFormat.SRGB,
                    data=rgb_frame,
                )
                result = landmarker.detect_for_video(mp_image, timestamp_ms)
                landmarks = {}

                if result.pose_landmarks:
                    for name, landmark in zip(LANDMARK_NAMES, result.pose_landmarks[0]):
                        landmarks[name] = {
                            "x": _round_float(landmark.x),
                            "y": _round_float(landmark.y),
                            "z": _round_float(landmark.z),
                            "visibility": _round_float(landmark.visibility),
                        }

                keypoint_frames.append(
                    {
                        "frame_index": frame_index,
                        "timestamp_ms": timestamp_ms,
                        "landmarks": landmarks,
                    }
                )
                skeleton_frames.append(
                    {
                        "frame_index": frame_index,
                        "timestamp_ms": timestamp_ms,
                        "nodes": map_landmarks_to_nodes(landmarks),
                    }
                )
                frame_index += 1
    finally:
        capture.release()

    keypoints_absolute, skeleton_absolute, keypoints_relative, skeleton_relative = _build_output_paths(
        video_id,
        sample_fps,
    )

    keypoints_payload = {
        "video_id": video_id,
        "sample_fps": sample_fps,
        "frames": keypoint_frames,
    }
    skeleton_payload = {
        "video_id": video_id,
        "sample_fps": sample_fps,
        "frames": skeleton_frames,
    }

    keypoints_absolute.parent.mkdir(parents=True, exist_ok=True)
    skeleton_absolute.parent.mkdir(parents=True, exist_ok=True)
    keypoints_absolute.write_text(
        json.dumps(keypoints_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    skeleton_absolute.write_text(
        json.dumps(skeleton_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    return {
        "video_id": video_id,
        "sample_fps": sample_fps,
        "keypoints_path": keypoints_relative,
        "skeleton_path": skeleton_relative,
        "frame_count": len(keypoint_frames),
        "landmark_names": LANDMARK_NAMES,
        "display_node_names": DISPLAY_NODE_NAMES,
    }
