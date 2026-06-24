# Dance Motion Analysis System / 舞蹈动作视频比对与纠错系统

## 中文说明

这是一个**毕设级 / 课程设计级 Flask Web 项目**，用于将学习者舞蹈视频与标准参考视频进行对比，并返回可视化分析和练习反馈。

该项目是匿名化整理后的**委托开发 / 协作开发项目案例**，用于展示 Flask 工程结构、服务层拆分、视频流程页面、动作比对逻辑和测试意识。它不应被描述为资深企业级生产系统。

已排除学校材料、论文、答辩文件、视频素材、运行期记录、模型二进制文件和个人标识。

## English

This is a **graduation-project-level / course-project-level Flask web project** for comparing learner dance videos with standard reference videos and returning visual analysis and practice feedback.

It is an anonymized **commissioned / collaborative development case** prepared for portfolio review. It demonstrates Flask application structure, service-layer separation, video workflow pages, motion comparison logic, and testing awareness, but it should not be described as a senior enterprise production system.

School-specific materials, papers, defense files, video assets, runtime records, model binaries, and personal identifiers are excluded.

## Tech Stack / 技术栈

- Python
- Flask
- Jinja templates
- HTML / CSS / JavaScript
- pytest

## What It Demonstrates / 展示重点

- Flask application structure with route blueprints / 使用蓝图组织 Flask 路由结构
- service-layer separation for video records, preview, comparison, and feedback logic / 将视频记录、预览、比对和反馈逻辑拆分到服务层
- static frontend pages for upload, preview, analysis, review, and history flows / 上传、预览、分析、复盘和历史记录等页面流程
- lightweight JSON-based runtime record handling for local demonstration / 用轻量 JSON 记录支持本地演示
- unit, API, and simple E2E tests / 单元测试、接口测试和简单端到端测试

## Layout / 目录结构

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

## Run Locally / 本地运行

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python run.py
```

## Tests / 测试

```powershell
.\.venv\Scripts\python -m pytest
```

## Limitations / 项目限制

- Local demonstration storage is file-based, not a production database design. / 本地演示存储基于文件，不是生产级数据库设计。
- MediaPipe model files and sample videos are not committed. / MediaPipe 模型文件和示例视频未提交到仓库。
- The UI and algorithm rules are suitable for academic demonstration, not professional sports or medical assessment. / UI 和算法规则适合毕设演示，不适合专业体育或医疗评估。
- Deployment hardening, observability, permission systems, and large-file handling would need more work before production use. / 若用于真实生产，需要继续完善部署加固、可观测性、权限系统和大文件处理。

