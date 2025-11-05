package com.gengzi.config;


import com.gengzi.response.BusinessException;
import com.gengzi.response.Result;
import com.gengzi.response.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        logger.error("业务异常:{} StackTrace:{}", e.getMessage(), e.fillInStackTrace(), e);
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