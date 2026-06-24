from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from app.config import Config


def _remove_child(path: Path) -> None:
    if path.is_dir():
        shutil.rmtree(path)
        return
    path.unlink(missing_ok=True)


def _clear_directory(directory: Path, preserve_names: set[str]) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for child in directory.iterdir():
        if child.name in preserve_names:
            continue
        _remove_child(child)


def reset_runtime_storage(keep_model: bool = True) -> None:
    storage_root = Path(Config.STORAGE_ROOT)

    managed_directories = [
        storage_root / "uploads" / "standard",
        storage_root / "uploads" / "learner",
        storage_root / "outputs" / "browser_sources" / "standard",
        storage_root / "outputs" / "browser_sources" / "learner",
        storage_root / "outputs" / "comparisons",
        storage_root / "outputs" / "keypoints",
        storage_root / "outputs" / "previews",
        storage_root / "outputs" / "templates",
        storage_root / "outputs" / "thumbnails" / "standard",
        storage_root / "outputs" / "thumbnails" / "learner",
    ]

    for directory in managed_directories:
        _clear_directory(directory, preserve_names={".gitkeep"})

    Config.VIDEO_RECORDS_FILE.parent.mkdir(parents=True, exist_ok=True)
    Config.ANALYSIS_RECORDS_FILE.parent.mkdir(parents=True, exist_ok=True)
    Config.VIDEO_RECORDS_FILE.write_text("[]", encoding="utf-8")
    Config.ANALYSIS_RECORDS_FILE.write_text("[]", encoding="utf-8")

    model_root = Path(Config.MODEL_ROOT)
    model_root.mkdir(parents=True, exist_ok=True)
    if not keep_model:
        for child in model_root.iterdir():
            _remove_child(child)


def main() -> None:
    parser = argparse.ArgumentParser(description="Reset runtime uploads, caches, and records for a clean test state.")
    parser.add_argument(
        "--drop-model",
        action="store_true",
        help="Also remove downloaded model files under storage/models.",
    )
    args = parser.parse_args()
    reset_runtime_storage(keep_model=not args.drop_model)


if __name__ == "__main__":
    main()
