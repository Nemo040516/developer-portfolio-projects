/**
 * @file 速览索引
 * @summary 业务异常基类，用于在服务层抛出“可预期业务错误码 + 提示信息”。
 * @core 1. 承载业务错误码 `code`
 * @core 2. 继承 `RuntimeException` 透传错误消息
 * @entry 先看：构造函数、getCode
 * @deps 关键依赖：GlobalExceptionHandler、各业务 Service
 * @risk 高风险修改点：错误码含义变更会影响前端提示与测试断言
 * @link 相关文件：后端/src/main/java/com/wms/backend/exception/GlobalExceptionHandler.java
 */
package com.wms.backend.exception;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
