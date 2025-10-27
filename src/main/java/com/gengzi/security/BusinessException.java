package com.gengzi.security;


import com.gengzi.response.ResultCode;

public class BusinessException extends RuntimeException {
    private final Integer code;

    // 构造器：传入状态码和消息
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    // 构造器：传入 ResultCode 枚举
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    // Getter
    public Integer getCode() {
        return code;
    }
}