import json
from pathlib import Path
from threading import Lock
from typing import Any


_STORE_LOCK = Lock()


def read_records(file_path: Path) -> list[dict[str, Any]]:
    if not file_path.exists():
        return []

    content = file_path.read_text(encoding="utf-8").strip()
    if not content:
        return []

    data = json.loads(content)
    if not isinstance(data, list):
        raise ValueError(f"invalid record store: {file_path}")
    return data


def write_records(file_path: Path, records: list[dict[str, Any]]) -> None:
    file_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = file_path.with_suffix(f"{file_path.suffix}.tmp")
    temp_path.write_text(
        json.dumps(records, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temp_path.replace(file_path)


def update_records(
    file_path: Path,
    updater,
) -> Any:
    with _STORE_LOCK:
        records = read_records(file_path)
        result = updater(records)
        write_records(file_path, records)
        return result
