/**
 * @file 速览索引
 * @summary 分页结果模型，封装总条数与当前页记录集合。
 * @core 1. 保存总记录数 `total`
 * @core 2. 保存当前页数据 `records`
 * @entry 先看：record 字段 `total、records`
 * @deps 关键依赖：各业务 Service 分页接口
 * @risk 高风险修改点：字段名变更会影响前端分页组件取值
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java
 */
package com.wms.backend.common;

import java.util.List;

public record PageResult<T>(long total, List<T> records) {
}
