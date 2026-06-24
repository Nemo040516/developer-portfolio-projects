/**
 * @file 速览索引
 * @summary 全局异常处理器，负责将后端异常统一转换为标准 `ApiResponse` 错误结构。
 * @core 1. 处理业务异常 `BusinessException`
 * @core 2. 处理参数校验异常（Bean Validation）
 * @core 3. 处理请求体格式异常与兜底系统异常
 * @entry 先看：handleBusiness、handleValid、handleBadRequest、handleOther
 * @deps 关键依赖：ApiResponse、BusinessException、Spring Validation
 * @risk 高风险修改点：错误码口径（4000/4001/5000）与前端统一提示文案
 * @link 相关文件：后端/src/main/java/com/wms/backend/common/ApiResponse.java
 */
package com.wms.backend.exception;

import com.wms.backend.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return ApiResponse.fail(4001, message);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ApiResponse<Void> handleBadRequest(Exception ex) {
        return ApiResponse.fail(4000, "请求参数错误");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception ex) {
        return ApiResponse.fail(5000, "服务器内部错误");
    }
}
