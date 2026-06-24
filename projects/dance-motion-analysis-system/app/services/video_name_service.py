import re
from pathlib import Path
from urllib.parse import unquote


_FALLBACK_LABELS = {
    "standard": "示范动作",
    "learner": "练习视频",
}

_NOISY_WORDS = {
    "sample",
    "samples",
    "demo",
    "mock",
    "tmp",
    "temp",
    "test",
}


def _normalize_candidate(filename: str | None) -> str:
    stem = Path(str(filename or "")).stem
    candidate = unquote(stem).replace("+", " ").strip()
    candidate = re.sub(r"[_\-]+", " ", candidate)
    candidate = re.sub(r"\s+", " ", candidate)
    return candidate.strip(" ._-/")


def _is_noisy_filename(candidate: str) -> bool:
    if not candidate:
        return True

    normalized = re.sub(r"[\s._-]+", "", candidate.lower())
    if not normalized:
        return True

    if re.fullmatch(r"[a-f0-9]{24,64}", normalized):
        return True

    if re.fullmatch(r"(mmexport|wxcamera|weixin|wechat|vid|video|img)?\d{10,}", normalized):
        return True

    if re.fullmatch(r"(mmexport|wxcamera|weixin|wechat|vid|video|img)[a-z0-9]{10,}", normalized):
        return True

    digit_count = sum(character.isdigit() for character in normalized)
    if len(normalized) >= 18 and digit_count / len(normalized) >= 0.65:
        return True

    words = [word for word in re.split(r"[\s._-]+", candidate.lower()) if word]
    if any(word in _NOISY_WORDS for word in words):
        return True
    if "temporal" in words and "warp" in words:
        return True

    return False


def _build_fallback_name(video_type: str | None, video_id: int | None) -> str:
    base_label = _FALLBACK_LABELS.get(str(video_type or "").lower(), "视频")
    if isinstance(video_id, int) and video_id > 0:
        return f"{base_label} {video_id:02d}"
    return base_label


def build_video_display_name(
    original_filename: str | None,
    video_type: str | None,
    video_id: int | None = None,
) -> str:
    candidate = _normalize_candidate(original_filename)
    if _is_noisy_filename(candidate):
        return _build_fallback_name(video_type, video_id)
    return candidate
