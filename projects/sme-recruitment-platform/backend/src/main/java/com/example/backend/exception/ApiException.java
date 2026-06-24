/*
 * 文件速览：
 * 1. 文件职责：承载接口层可预期业务异常，允许直接携带 HTTP 风格状态码与提示文案。
 * 2. 对外入口：构造函数 ApiException(code, message) 与 getCode。
 * 3. 关键结构：继承 RuntimeException，仅补充 code 字段。
 * 4. 阅读建议：结合 GlobalExceptionHandler 与 ControllerAccessUtils 一起看。
 */
package com.example.backend.exception;

/**
 * 接口层业务异常。
 */
public class ApiException extends RuntimeException {

    private final Integer code;

    public ApiException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
