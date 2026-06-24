from __future__ import annotations

from pathlib import Path
from typing import Any

from flask import current_app

from .json_store import read_records, write_records


TRANSIENT_SUBDIRECTORIES = (
    Path("uploads") / "standard",
    Path("uploads") / "learner",
    Path("outputs") / "keypoints",
    Path("outputs") / "templates",
    Path("outputs") / "previews",
    Path("outputs") / "thumbnails" / "standard",
    Path("outputs") / "thumbnails" / "learner",
    Path("outputs") / "browser_sources" / "standard",
    Path("outputs") / "browser_sources" / "learner",
)


def _count_files(directory: Path) -> int:
    if not directory.exists():
        return 0
    return sum(1 for path in directory.rglob("*") if path.is_file())


def _clear_directory(directory: Path) -> int:
    if not directory.exists():
        directory.mkdir(parents=True, exist_ok=True)
        return 0

    deleted_files = _count_files(directory)

    for child in directory.iterdir():
        if child.is_dir():
            for nested in sorted(child.rglob("*"), reverse=True):
                if nested.is_file():
                    nested.unlink(missing_ok=True)
                elif nested.is_dir():
                    nested.rmdir()
            child.rmdir()
            continue
        child.unlink(missing_ok=True)

    directory.mkdir(parents=True, exist_ok=True)
    return deleted_files


def _clear_unsaved_analysis_outputs(storage_root: Path, analysis_records_file: Path) -> dict[str, Any]:
    analysis_records = read_records(analysis_records_file)
    saved_records = [record for record in analysis_records if record.get("is_saved")]
    write_records(analysis_records_file, saved_records)

    comparisons_dir = storage_root / "outputs" / "comparisons"
    keep_names = {
        Path(record["result_json_path"]).name
        for record in saved_records
        if record.get("result_json_path")
    }

    cleared_result_files = 0
    if comparisons_dir.exists():
        for result_file in comparisons_dir.glob("*.json"):
            if result_file.name in keep_names:
                continue
            result_file.unlink(missing_ok=True)
            cleared_result_files += 1

    return {
        "cleared_unsaved_analysis_records": max(0, len(analysis_records) - len(saved_records)),
        "cleared_unsaved_result_files": cleared_result_files,
        "saved_analysis_records": len(saved_records),
    }


def purge_transient_storage(storage_root: Path, video_records_file: Path, analysis_records_file: Path) -> dict[str, Any]:
    cleared_files = 0
    cleared_dirs = 0

    for relative_dir in TRANSIENT_SUBDIRECTORIES:
        absolute_dir = storage_root / relative_dir
        cleared_files += _clear_directory(absolute_dir)
        cleared_dirs += 1

    video_records = read_records(video_records_file)
    write_records(video_records_file, [])
    analysis_cleanup = _clear_unsaved_analysis_outputs(storage_root, analysis_records_file)

    return {
        "cleared_files": cleared_files,
        "cleared_directories": cleared_dirs,
        "cleared_video_records": len(video_records),
        **analysis_cleanup,
    }


def purge_transient_storage_from_current_app() -> dict[str, Any]:
    return purge_transient_storage(
        Path(current_app.config["STORAGE_ROOT"]),
        Path(current_app.config["VIDEO_RECORDS_FILE"]),
        Path(current_app.config["ANALYSIS_RECORDS_FILE"]),
    )
