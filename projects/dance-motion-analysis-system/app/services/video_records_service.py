import hashlib
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from flask import current_app
from werkzeug.datastructures import FileStorage
from werkzeug.utils import secure_filename

from .json_store import read_records, update_records, write_records
from .video_name_service import build_video_display_name
from .video_metadata_service import read_video_metadata
from .video_thumbnail_service import write_video_thumbnail


def _now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _allowed_extension(filename: str) -> bool:
    if "." not in filename:
        return False

    extension = filename.rsplit(".", 1)[1].lower()
    return extension in current_app.config["ALLOWED_VIDEO_EXTENSIONS"]


def _build_thumbnail_relative_path(video: dict) -> Path:
    stored_filename = video.get("stored_filename") or f"video_{video.get('id', 'unknown')}.mp4"
    return (
        Path("storage")
        / "outputs"
        / "thumbnails"
        / str(video.get("video_type") or "unknown")
        / f"{Path(stored_filename).stem}.jpg"
    )


def _compute_file_sha256(file_path: Path) -> str:
    digest = hashlib.sha256()
    with file_path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(1024 * 1024), b""):
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def ensure_video_thumbnail_path(video: dict) -> str | None:
    existing_thumbnail_path = video.get("thumbnail_path")
    if existing_thumbnail_path:
        absolute_thumbnail_path = current_app.config["STORAGE_ROOT"].parent / existing_thumbnail_path
        if absolute_thumbnail_path.exists():
            return existing_thumbnail_path

    absolute_video_path = resolve_video_file_path(video)
    if not absolute_video_path.exists():
        return None

    relative_thumbnail_path = _build_thumbnail_relative_path(video)
    absolute_thumbnail_path = current_app.config["STORAGE_ROOT"].parent / relative_thumbnail_path
    if absolute_thumbnail_path.exists():
        return relative_thumbnail_path.as_posix()

    created = write_video_thumbnail(absolute_video_path, absolute_thumbnail_path)
    if not created or not absolute_thumbnail_path.exists():
        return None

    return relative_thumbnail_path.as_posix()


def list_video_records(video_type: str | None = None) -> list[dict]:
    records = read_records(current_app.config["VIDEO_RECORDS_FILE"])
    changed = False
    valid_records = []
    for record in records:
        absolute_video_path = resolve_video_file_path(record)
        if not absolute_video_path.exists():
            changed = True
            continue

        display_name = build_video_display_name(
            record.get("original_filename"),
            record.get("video_type"),
            record.get("id"),
        )
        if record.get("display_name") != display_name:
            record["display_name"] = display_name
            changed = True

        next_thumbnail_path = ensure_video_thumbnail_path(record)
        if next_thumbnail_path and record.get("thumbnail_path") != next_thumbnail_path:
            record["thumbnail_path"] = next_thumbnail_path
            changed = True
        if not next_thumbnail_path and record.get("thumbnail_path"):
            record.pop("thumbnail_path", None)
            changed = True

        if not record.get("content_hash"):
            if absolute_video_path.exists():
                record["content_hash"] = _compute_file_sha256(absolute_video_path)
                changed = True
        valid_records.append(record)

    if changed:
        write_records(current_app.config["VIDEO_RECORDS_FILE"], valid_records)

    records = valid_records
    if video_type is None:
        return records
    return [record for record in records if record["video_type"] == video_type]


def get_video_record(video_id: int) -> dict | None:
    return next(
        (record for record in list_video_records() if record["id"] == video_id),
        None,
    )


def resolve_video_file_path(video: dict) -> Path:
    return current_app.config["STORAGE_ROOT"].parent / video["file_path"]


def resolve_thumbnail_file_path(video: dict) -> Path:
    thumbnail_path = video.get("thumbnail_path")
    if thumbnail_path:
        return current_app.config["STORAGE_ROOT"].parent / thumbnail_path
    return current_app.config["STORAGE_ROOT"].parent / _build_thumbnail_relative_path(video)


def _get_next_video_record_id(records: list[dict]) -> int:
    next_id = max((int(record.get("id", 0)) for record in records), default=0)
    analysis_records = read_records(current_app.config["ANALYSIS_RECORDS_FILE"])
    for analysis in analysis_records:
        next_id = max(
            next_id,
            int(analysis.get("standard_video_id", 0) or 0),
            int(analysis.get("learner_video_id", 0) or 0),
        )
    return next_id + 1


def create_video_record(file: FileStorage, video_type: str) -> dict:
    filename = secure_filename(file.filename or "")
    if not filename:
        raise ValueError("missing file")
    if not _allowed_extension(filename):
        raise ValueError("invalid file type")

    extension = filename.rsplit(".", 1)[1].lower()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    suffix = uuid4().hex[:8]
    stored_filename = f"{timestamp}_{video_type}_{suffix}.{extension}"
    relative_path = Path("storage") / "uploads" / video_type / stored_filename
    absolute_path = current_app.config["STORAGE_ROOT"] / "uploads" / video_type / stored_filename

    file.save(absolute_path)
    try:
        metadata = read_video_metadata(absolute_path)
    except Exception:
        absolute_path.unlink(missing_ok=True)
        raise
    content_hash = _compute_file_sha256(absolute_path)

    thumbnail_relative_path = None
    thumbnail_absolute_path = (
        current_app.config["STORAGE_ROOT"]
        / "outputs"
        / "thumbnails"
        / video_type
        / f"{Path(stored_filename).stem}.jpg"
    )
    if write_video_thumbnail(absolute_path, thumbnail_absolute_path):
        thumbnail_relative_path = (
            Path("storage")
            / "outputs"
            / "thumbnails"
            / video_type
            / f"{Path(stored_filename).stem}.jpg"
        ).as_posix()

    def updater(records: list[dict]) -> dict:
        next_id = _get_next_video_record_id(records)
        created_at = _now_text()
        record = {
            "id": next_id,
            "video_type": video_type,
            "original_filename": filename,
            "display_name": build_video_display_name(filename, video_type, next_id),
            "stored_filename": stored_filename,
            "file_path": relative_path.as_posix(),
            "content_hash": content_hash,
            "created_at": created_at,
            **metadata,
        }
        if thumbnail_relative_path:
            record["thumbnail_path"] = thumbnail_relative_path
        records.append(record)
        return record

    return update_records(current_app.config["VIDEO_RECORDS_FILE"], updater)
