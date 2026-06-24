# E2E Tests / 端到端测试

- `specs/`: Playwright test specs / Playwright 测试脚本
- `fixtures/`: fixture notes only / 测试夹具说明
- `support/`: shared helpers / 通用辅助方法
- `session-state/`: local custom session cache; JSON files are ignored / 本地自定义登录态缓存，JSON 文件不入库

## Notes / 说明

- The app stores auth state in `sessionStorage`, so the tests use custom session-state JSON plus `page.addInitScript`.
- Session JSON files can contain local JWT values and are ignored by `.gitignore`.
- Upload tests use in-memory fixture builders instead of committed resume or evidence files.

- 当前项目登录态保存在 `sessionStorage`，测试使用自定义 session-state JSON 与 `page.addInitScript`。
- 登录态 JSON 可能包含本地 JWT，因此被 `.gitignore` 忽略。
- 上传测试使用内存夹具构造，不提交简历或证据文件。
