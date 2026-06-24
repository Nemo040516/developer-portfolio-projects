# Developer Portfolio Projects / 求职作品集项目

## 中文说明

这个仓库用于简历和面试作品展示，收录的是我整理过的**毕设级 / 课程设计级小项目**。项目可以展示前后端开发、接口设计、数据库脚本、测试用例和项目文档整理能力，但不应包装成资深工程师参与的大型企业级落地系统。

仓库中的项目已按功能重新命名，并做了公开仓库整理。主展示项目是我的自有本科毕业设计；补充项目是匿名化的委托开发 / 协作开发项目案例。公开仓库只展示可讨论的技术实现，不公开个人姓名、学校材料、论文、答辩资料、视频素材、本机私有配置或运行期隐私数据。

## English

This repository is a resume and interview portfolio. It contains selected **undergraduate graduation-project-level / course-project-level projects** reorganized for public technical review.

The projects are honest small-scale portfolio examples. They demonstrate full-stack implementation, API design, database scripts, test coverage, and documentation habits, but they should not be described as senior-level enterprise production systems.

The main showcase project is my own undergraduate graduation project. The additional projects are published as anonymized commissioned / collaborative development cases. Only public code, tests, and engineering structure are included. Personal identifiers, school-specific materials, papers, defense files, video assets, private local configuration, and runtime data are excluded.

## Main Showcase Project / 主展示项目

| Project | 中文名称 | Source / 来源 | Tech Stack / 技术栈 | What It Shows / 展示重点 |
| --- | --- | --- | --- | --- |
| [SME Recruitment and Application Management Platform](projects/sme-recruitment-platform) | 中小商家招聘与投递管理平台 | My own undergraduate graduation project / 我的自有本科毕业设计项目 | Vue 3, Vite, Element Plus, Pinia, Spring Boot, Spring Security, JWT, MyBatis-Plus, MySQL, WebSocket, Playwright | Role-based workflows, recruitment lifecycle, resume/application management, chat, governance notices, upload access control, tests / 多角色流程、招聘生命周期、简历与投递管理、即时沟通、平台治理通知、上传访问控制、测试 |

## Additional Cases / 补充项目案例

| Project | 中文名称 | Source / 来源 | Tech Stack / 技术栈 | What It Shows / 展示重点 |
| --- | --- | --- | --- | --- |
| [Warehouse Management System](projects/warehouse-management-system) | 仓储管理系统 | Anonymized commissioned / collaborative case / 匿名化委托或协作案例 | Vue 3, Vite, Spring Boot, MySQL, Playwright | CRUD workflows, role-based menus, inventory flows, SQL design, integration/E2E tests / 业务流程、权限菜单、库存流转、数据库脚本、测试 |
| [Dance Motion Analysis System](projects/dance-motion-analysis-system) | 舞蹈动作视频比对与纠错系统 | Anonymized commissioned / collaborative case / 匿名化委托或协作案例 | Python, Flask, HTML/CSS/JavaScript, pytest | Flask routing, service-layer design, video workflow pages, motion comparison logic, tests / Flask 结构、服务层拆分、视频流程页面、动作比对逻辑、测试 |

## Repository Layout / 仓库结构

```text
developer-portfolio-projects/
  README.md
  PROJECT_SCOPE.md
  projects/
    sme-recruitment-platform/
      backend/
      frontend/
      scripts/
      README.md
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

## How to Read / 如何阅读

For interview review, start with the main project README, then inspect the backend services, frontend views/components, database scripts, and test cases.

面试或代码审阅时，建议先看主展示项目 README，再看后端服务层、前端页面与组件、数据库脚本和测试用例。

The goal is not to claim large-scale production experience. The goal is to provide concrete code samples that can be discussed honestly in an interview.

这个仓库的目标不是证明大型生产项目经验，而是提供可以在面试中真实讨论的代码样例。
