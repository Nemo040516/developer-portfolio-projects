from math import dist


def _round_point(x: float, y: float) -> dict[str, float]:
    return {
        "x": round(float(x), 4),
        "y": round(float(y), 4),
    }


def _midpoint(a: dict, b: dict) -> dict[str, float]:
    return _round_point((a["x"] + b["x"]) / 2, (a["y"] + b["y"]) / 2)


def _weighted_point(a: dict, b: dict, ratio: float) -> dict[str, float]:
    return _round_point(
        a["x"] + (b["x"] - a["x"]) * ratio,
        a["y"] + (b["y"] - a["y"]) * ratio,
    )


def _average_points(*points: dict) -> dict[str, float]:
    count = len(points)
    return _round_point(
        sum(point["x"] for point in points) / count,
        sum(point["y"] for point in points) / count,
    )


def _round_point_3d(x: float, y: float, z: float) -> dict[str, float]:
    return {
        "x": round(float(x), 4),
        "y": round(float(y), 4),
        "z": round(float(z), 4),
    }


def _midpoint_3d(a: dict, b: dict) -> dict[str, float]:
    return _round_point_3d(
        (a["x"] + b["x"]) / 2,
        (a["y"] + b["y"]) / 2,
        (a["z"] + b["z"]) / 2,
    )


def _weighted_point_3d(a: dict, b: dict, ratio: float) -> dict[str, float]:
    return _round_point_3d(
        a["x"] + (b["x"] - a["x"]) * ratio,
        a["y"] + (b["y"] - a["y"]) * ratio,
        a["z"] + (b["z"] - a["z"]) * ratio,
    )


def _average_points_3d(*points: dict) -> dict[str, float]:
    count = len(points)
    return _round_point_3d(
        sum(point["x"] for point in points) / count,
        sum(point["y"] for point in points) / count,
        sum(point["z"] for point in points) / count,
    )


def map_landmarks_to_nodes(landmarks: dict[str, dict]) -> dict[str, dict]:
    required = (
        "nose",
        "left_ear",
        "right_ear",
        "left_shoulder",
        "right_shoulder",
        "left_elbow",
        "right_elbow",
        "left_wrist",
        "right_wrist",
        "left_index",
        "right_index",
        "left_pinky",
        "right_pinky",
        "left_hip",
        "right_hip",
        "left_knee",
        "right_knee",
        "left_ankle",
        "right_ankle",
        "left_heel",
        "right_heel",
        "left_foot_index",
        "right_foot_index",
    )
    if not all(name in landmarks for name in required):
        return {}

    shoulder_center = _midpoint(landmarks["left_shoulder"], landmarks["right_shoulder"])
    hip_center = _midpoint(landmarks["left_hip"], landmarks["right_hip"])
    head_center = _average_points(
        landmarks["nose"],
        landmarks["left_ear"],
        landmarks["right_ear"],
    )
    shoulder_width = dist(
        (landmarks["left_shoulder"]["x"], landmarks["left_shoulder"]["y"]),
        (landmarks["right_shoulder"]["x"], landmarks["right_shoulder"]["y"]),
    )

    return {
        "head_circle": {
            "cx": round(head_center["x"], 4),
            "cy": round(head_center["y"], 4),
            "r": round(max(shoulder_width * 0.28, 0.03), 4),
        },
        "neck": shoulder_center,
        "left_shoulder": _round_point(
            landmarks["left_shoulder"]["x"],
            landmarks["left_shoulder"]["y"],
        ),
        "right_shoulder": _round_point(
            landmarks["right_shoulder"]["x"],
            landmarks["right_shoulder"]["y"],
        ),
        "left_elbow": _round_point(
            landmarks["left_elbow"]["x"],
            landmarks["left_elbow"]["y"],
        ),
        "right_elbow": _round_point(
            landmarks["right_elbow"]["x"],
            landmarks["right_elbow"]["y"],
        ),
        "left_hand": _average_points(
            landmarks["left_wrist"],
            landmarks["left_index"],
            landmarks["left_pinky"],
        ),
        "right_hand": _average_points(
            landmarks["right_wrist"],
            landmarks["right_index"],
            landmarks["right_pinky"],
        ),
        "chest": _weighted_point(shoulder_center, hip_center, 1 / 3),
        "abdomen": _weighted_point(shoulder_center, hip_center, 2 / 3),
        "left_hip": _round_point(landmarks["left_hip"]["x"], landmarks["left_hip"]["y"]),
        "right_hip": _round_point(landmarks["right_hip"]["x"], landmarks["right_hip"]["y"]),
        "left_knee": _round_point(landmarks["left_knee"]["x"], landmarks["left_knee"]["y"]),
        "right_knee": _round_point(landmarks["right_knee"]["x"], landmarks["right_knee"]["y"]),
        "left_foot": _average_points(
            landmarks["left_ankle"],
            landmarks["left_heel"],
            landmarks["left_foot_index"],
        ),
        "right_foot": _average_points(
            landmarks["right_ankle"],
            landmarks["right_heel"],
            landmarks["right_foot_index"],
        ),
    }


def map_landmarks_to_nodes_3d(landmarks: dict[str, dict]) -> dict[str, dict]:
    required = (
        "nose",
        "left_ear",
        "right_ear",
        "left_shoulder",
        "right_shoulder",
        "left_elbow",
        "right_elbow",
        "left_wrist",
        "right_wrist",
        "left_index",
        "right_index",
        "left_pinky",
        "right_pinky",
        "left_hip",
        "right_hip",
        "left_knee",
        "right_knee",
        "left_ankle",
        "right_ankle",
        "left_heel",
        "right_heel",
        "left_foot_index",
        "right_foot_index",
    )
    if not all(name in landmarks for name in required):
        return {}

    shoulder_center = _midpoint_3d(landmarks["left_shoulder"], landmarks["right_shoulder"])
    hip_center = _midpoint_3d(landmarks["left_hip"], landmarks["right_hip"])
    head_center = _average_points_3d(
        landmarks["nose"],
        landmarks["left_ear"],
        landmarks["right_ear"],
    )

    return {
        "head": head_center,
        "neck": shoulder_center,
        "left_shoulder": _round_point_3d(
            landmarks["left_shoulder"]["x"],
            landmarks["left_shoulder"]["y"],
            landmarks["left_shoulder"]["z"],
        ),
        "right_shoulder": _round_point_3d(
            landmarks["right_shoulder"]["x"],
            landmarks["right_shoulder"]["y"],
            landmarks["right_shoulder"]["z"],
        ),
        "left_elbow": _round_point_3d(
            landmarks["left_elbow"]["x"],
            landmarks["left_elbow"]["y"],
            landmarks["left_elbow"]["z"],
        ),
        "right_elbow": _round_point_3d(
            landmarks["right_elbow"]["x"],
            landmarks["right_elbow"]["y"],
            landmarks["right_elbow"]["z"],
        ),
        "left_hand": _average_points_3d(
            landmarks["left_wrist"],
            landmarks["left_index"],
            landmarks["left_pinky"],
        ),
        "right_hand": _average_points_3d(
            landmarks["right_wrist"],
            landmarks["right_index"],
            landmarks["right_pinky"],
        ),
        "chest": _weighted_point_3d(shoulder_center, hip_center, 1 / 3),
        "abdomen": _weighted_point_3d(shoulder_center, hip_center, 2 / 3),
        "left_hip": _round_point_3d(
            landmarks["left_hip"]["x"],
            landmarks["left_hip"]["y"],
            landmarks["left_hip"]["z"],
        ),
        "right_hip": _round_point_3d(
            landmarks["right_hip"]["x"],
            landmarks["right_hip"]["y"],
            landmarks["right_hip"]["z"],
        ),
        "left_knee": _round_point_3d(
            landmarks["left_knee"]["x"],
            landmarks["left_knee"]["y"],
            landmarks["left_knee"]["z"],
        ),
        "right_knee": _round_point_3d(
            landmarks["right_knee"]["x"],
            landmarks["right_knee"]["y"],
            landmarks["right_knee"]["z"],
        ),
        "left_foot": _average_points_3d(
            landmarks["left_ankle"],
            landmarks["left_heel"],
            landmarks["left_foot_index"],
        ),
        "right_foot": _average_points_3d(
            landmarks["right_ankle"],
            landmarks["right_heel"],
            landmarks["right_foot_index"],
        ),
    }
