/**
 * @file 速览索引
 * @summary 统一接口响应模型，封装业务码、消息与数据体。
 * @core 1. 统一成功结构 `code=0`
 * @core 2. 统一失败结构 `code!=0`
 * @core 3. 提供 `success/fail` 静态工厂方法
 * @entry 先看：success、fail
 * @deps 关键依赖：全量 Controller 与 GlobalExceptionHandler
 * @risk 高风险修改点：字段命名与返回码约定（影响前后端全链路）
 * @link 相关文件：后端/src/main/java/com/wms/backend/exception/GlobalExceptionHandler.java
 */
package com.wms.backend.common;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
