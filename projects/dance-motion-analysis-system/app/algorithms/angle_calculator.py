from math import acos, sqrt


ANGLE_KEYS = (
    "left_elbow",
    "right_elbow",
    "left_knee",
    "right_knee",
)

TRAJECTORY_NODE_NAMES = (
    "left_hand",
    "right_hand",
    "left_foot",
    "right_foot",
)


def _round_float(value: float) -> float:
    return round(float(value), 4)


def _distance(a: dict, b: dict) -> float:
    return sqrt((a["x"] - b["x"]) ** 2 + (a["y"] - b["y"]) ** 2)


def calculate_angle(point_a: dict | None, point_b: dict | None, point_c: dict | None) -> float | None:
    if point_a is None or point_b is None or point_c is None:
        return None

    length_ab = _distance(point_a, point_b)
    length_cb = _distance(point_c, point_b)
    if length_ab == 0 or length_cb == 0:
        return None

    vector_ba = (
        point_a["x"] - point_b["x"],
        point_a["y"] - point_b["y"],
    )
    vector_bc = (
        point_c["x"] - point_b["x"],
        point_c["y"] - point_b["y"],
    )
    dot_product = (vector_ba[0] * vector_bc[0]) + (vector_ba[1] * vector_bc[1])
    cosine = dot_product / (length_ab * length_cb)
    cosine = max(-1.0, min(1.0, cosine))
    return _round_float(acos(cosine) * 180.0 / 3.141592653589793)


def calculate_joint_angles(nodes: dict[str, dict]) -> dict[str, float | None]:
    return {
        "left_elbow": calculate_angle(
            nodes.get("left_shoulder"),
            nodes.get("left_elbow"),
            nodes.get("left_hand"),
        ),
        "right_elbow": calculate_angle(
            nodes.get("right_shoulder"),
            nodes.get("right_elbow"),
            nodes.get("right_hand"),
        ),
        "left_knee": calculate_angle(
            nodes.get("left_hip"),
            nodes.get("left_knee"),
            nodes.get("left_foot"),
        ),
        "right_knee": calculate_angle(
            nodes.get("right_hip"),
            nodes.get("right_knee"),
            nodes.get("right_foot"),
        ),
    }
