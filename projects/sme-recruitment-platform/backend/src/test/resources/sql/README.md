# SQL 目录说明

- `base-schema.sql`：独立 MySQL 口径基础表结构
  - 当前已覆盖 `sys_user`、`applicant_profile`、`merchant_profile`、`job_category`、`job_post`、`sys_log_audit`、`report_info`、`report_evidence`、`governance_notice`、`governance_notice_action`
- `base-seed.sql`：真实 MySQL 口径首轮基础种子数据
- `seed-admin-query.sql`：管理侧真实 SQL 查询与统计专项种子数据
- `seed-admin-log-query.sql`：管理侧日志真实 SQL 排序与截断专项种子数据
- `reset-report-flow.sql`：举报链路数据清理与自增重置
- `reset-chat-flow.sql`：聊天链路重置脚本占位
- `reset-merchant-audit-flow.sql`：商家审核链路重置脚本占位

当前 SQL 已开始承接：

- Task 10：独立 MySQL 首轮口径
- Task 11：真实副作用扩展
- Task 12：管理侧真实 SQL 查询与统计聚合第一批
- Task 13：管理侧日志真实 SQL 排序与截断第一批

后续扩展建议：

- 若继续迁移聊天真实口径测试，可补 `chat_message`、`chat_session` 相关 DDL 与 reset 脚本。
- 若继续迁移更复杂文件链路，可补更多上传 / 替换 / 回滚专用种子与清理脚本。
- 若继续补管理侧日志真实 SQL，可追加日志排序 / 截断专用 seed。
- 若继续迁移治理通知更复杂场景，可在现有基础上追加用户动作、申诉与复核专用 seed。
