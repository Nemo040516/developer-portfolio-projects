/*
 * 文件速览：
 * 1. 文件职责：统一处理控制器抛出的接口异常与兜底系统异常。
 * 2. 对外入口：@RestControllerAdvice 自动拦截全局异常。
 * 3. 关键结构：ApiException 精确返回业务状态码，Exception 兜底记录日志。
 * 4. 阅读建议：先看 handleApiException，再看 handleException。
 */
package com.example.backend.exception;

import com.example.backend.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public Result<String> handleApiException(ApiException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        return Result.error(400, extractFirstBindingMessage(e.getBindingResult().getFieldErrors(), "请求参数校验失败"));
    }

    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        return Result.error(400, extractFirstBindingMessage(e.getBindingResult().getFieldErrors(), "请求参数绑定失败"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<String> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("请求参数校验失败");
        return Result.error(400, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return Result.error(400, "请求体格式错误或字段类型不合法");
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(e.getMessage());
    }

    private String extractFirstBindingMessage(List<FieldError> fieldErrors, String defaultMessage) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return defaultMessage;
        }
        FieldError fieldError = fieldErrors.get(0);
        return fieldError.getDefaultMessage() == null || fieldError.getDefaultMessage().isBlank()
                ? defaultMessage
                : fieldError.getDefaultMessage();
    }
}
