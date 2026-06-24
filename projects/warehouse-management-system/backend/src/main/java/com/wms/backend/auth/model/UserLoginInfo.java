/**
 * @file 速览索引
 * @summary 登录态用户模型，承载认证阶段从数据库读取的关键账号信息。
 * @core 1. 包含用户主键与账号凭据
 * @core 2. 包含用户状态 `status`
 * @core 3. 包含角色编码 `roleCode`
 * @entry 先看：record 字段 `userId、username、password、status、roleCode`
 * @deps 关键依赖：AuthRepository、AuthService
 * @risk 高风险修改点：字段名需与SQL查询别名保持一致
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/repository/AuthRepository.java
 */
package com.wms.backend.auth.model;

public record UserLoginInfo(
        Long userId,
        String username,
        String password,
        Integer status,
        String roleCode
) {
}
