# Developer Portfolio Projects

This repository contains selected undergraduate graduation-project-scale systems for job applications and technical review.

The projects here are honest portfolio examples: they are small academic / course / graduation-design level applications, not senior-level enterprise production systems. The code is kept public to show implementation ability, engineering habits, documentation, and testing awareness.

## 中文说明

这个仓库用于简历和面试作品展示。项目定位是**毕设级 / 课程设计级小项目**，不是非常高级、资深的企业级落地项目。

其中原始本地目录 `JXY`、`CWH` 下的内容，已整理为匿名化的**同学委托开发 / 协助完成毕业设计项目案例**。仓库只展示可公开的代码、测试和工程结构，不公开同学姓名、学校材料、论文、答辩资料、视频素材和本机私有配置。

## Scope Notice

- These are portfolio demonstrations based on local project work.
- `JXY` and `CWH` source folders have been anonymized and reorganized before publishing.
- The included projects are best described as **classmate-commissioned / collaborative graduation-project development cases**.
- Personal names, school materials, papers, defense documents, runtime data, videos, local credentials, and generated build outputs are intentionally excluded.

## Projects

| Project | Type | Tech Stack | What It Shows |
| --- | --- | --- | --- |
| [Warehouse Management System](projects/warehouse-management-system) | Graduation-project-level full-stack system | Vue 3, Vite, Spring Boot, MySQL, Playwright | CRUD workflows, role-based menus, inventory flows, SQL schema design, integration/E2E tests |
| [Dance Motion Analysis System](projects/dance-motion-analysis-system) | Graduation-project-level web application | Python, Flask, HTML/CSS/JavaScript, pytest | Flask routing, service-layer design, video workflow pages, motion comparison logic, test coverage |

## Repository Layout

```text
developer-portfolio-projects/
  README.md
  PROJECT_SCOPE.md
  projects/
    warehouse-management-system/
      frontend/
      backend/
      sql/
      README.md
    dance-motion-analysis-system/
      app/
      tests/
      scripts/
      README.md
```

## How to Read This Repository

Recruiters and interviewers can start with the project README files, then inspect:

- backend service and controller code
- frontend component structure
- SQL schema and initialization scripts
- integration tests and E2E tests
- project-level limitations and improvement notes

The goal is not to claim large-scale production experience. The goal is to provide concrete code samples that can be discussed in an interview.
