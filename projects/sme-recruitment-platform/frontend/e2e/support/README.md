# support 目录说明

当前阶段用于沉淀以下通用能力：

- 稳定选择器约定
- 三角色登录态生成与注入脚本
- 通用等待与断言辅助

说明：

- 当前项目使用 `sessionStorage` 保存登录态，因此这里会优先提供“自定义 session 登录态辅助”，而不是直接依赖 Playwright 原生 `storageState`。
- Task 1 先建立目录与基础 helper，后续按实际 E2E 用例逐步补充。
