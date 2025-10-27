package com.gengzi.security;


import com.gengzi.response.Result;
import com.gengzi.response.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器：自动将异常转为标准 Result 格式
 */
@RestControllerAdvice // 作用于所有 @RestController
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class.getName());

    // 专门捕获 BadCredentialsException
    @ExceptionHandler(BadCredentialsException.class)
    public Result<Map<String, String>> handleBadCredentialsException(BadCredentialsException e, HttpServletResponse response) {

        Map<String, String> errorMap = new HashMap<>();
        // 封装为标准失败格式
        return Result.fail(ResultCode.LOGIN_ERROR.getCode(),
                ResultCode.LOGIN_ERROR.getMessage(),
                errorMap);
    }

    // 捕获其他 Security 异常（可选）
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public Result<Map<String, String>> handleAuthenticationException(Exception e, HttpServletResponse response) {
        Map<String, String> errorMap = new HashMap<>();
        // 封装为标准失败格式
        return Result.fail(ResultCode.LOGIN_ERROR.getCode(),
                ResultCode.LOGIN_ERROR.getMessage(),
                errorMap);
    }

    /**
     * 1. 处理参数校验异常（@Valid 触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Map<String, String>> handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        Map<String, String> errorMap = new HashMap<>();
        // 收集字段错误信息
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            errorMap.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        // 封装为标准失败格式
        return Result.fail(ResultCode.PARAM_ERROR.getCode(),
                ResultCode.PARAM_ERROR.getMessage(),
                errorMap);
    }

    /**
     * 2. 处理自定义业务异常（需自己定义 BusinessException 类）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 3. 处理系统异常（兜底）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleSystemException(Exception e) {
        logger.error("系统异常:{} StackTrace:{}", e.getMessage(), e.fillInStackTrace(), e);
        return Result.fail(ResultCode.SYSTEM_ERROR.getCode(),
                ResultCode.SYSTEM_ERROR.getMessage());
    }
}