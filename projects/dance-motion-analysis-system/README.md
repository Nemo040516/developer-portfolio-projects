# Dance Motion Analysis System

A graduation-project-level Flask web application for comparing learner dance videos with standard reference videos and returning visual analysis / practice feedback.

## Portfolio Context

This is an anonymized **classmate-commissioned / collaborative graduation-project development case** reorganized for resume review. It is not presented as a senior enterprise production system.

Original school materials, papers, defense files, videos, runtime records, model binaries, and personal identifiers are excluded.

## Tech Stack

- Python
- Flask
- Jinja templates
- HTML / CSS / JavaScript
- pytest

## What It Demonstrates

- Flask application structure with route blueprints
- service-layer separation for video records, preview, comparison, and feedback logic
- static frontend pages for upload, preview, analysis, review, and history flows
- lightweight JSON-based runtime record handling for local demonstration
- unit, API, and simple E2E tests

## Layout

```text
dance-motion-analysis-system/
  app/
    api/
    algorithms/
    services/
    static/
    templates/
    config.py
    web.py
  scripts/
    dev/
  tests/
  run.py
  requirements.txt
```

## Run Locally

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python run.py
```

## Tests

```powershell
.\.venv\Scripts\python -m pytest
```

## Limitations

- Local demonstration storage is file-based, not a production database design.
- MediaPipe model files and sample videos are not committed.
- The UI and algorithm rules are suitable for academic demonstration, not professional sports or medical assessment.
- Deployment hardening, observability, permission systems, and large-file handling would need more work before production use.

