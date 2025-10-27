package com.gengzi.response;

import lombok.Data;

/**
 * 标准接口返回结构体
 * @param <T> 业务数据类型（如 User、List<User>、String 等）
 */
@Data
public class Result<T> {
    /**
     * 业务状态码：
     * - 200：成功
     * - 4xx：客户端错误（如参数错误、权限不足）
     * - 5xx：服务端错误（如数据库异常、接口异常）
     * - 1xxx：自定义业务异常（如用户不存在、订单已取消）
     */
    private Integer code;

    /**
     * 执行状态：true=成功，false=失败（便于前端快速判断）
     */
    private Boolean success;

    /**
     * 提示消息：成功时可返回"操作成功"，失败时返回具体原因（如"用户名已存在"）
     */
    private String message;

    /**
     * 业务数据：成功时返回具体数据，失败时返回 null 或空集合
     */
    private T data;

    // ------------------- 静态工厂方法（简化返回逻辑） -------------------
    /**
     * 成功返回（无业务数据，仅返回状态和消息）
     */
    public static <T> Result<T> successMessage(String message) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(null); // 无数据时显式设为 null
        return result;
    }

    /**
     * 成功返回（带业务数据）
     */
    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 成功返回（带业务数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setSuccess(true);
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 失败返回（带业务状态码和原因）
     */
    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setSuccess(false);
        result.setMessage(message);
        result.setData(null);
        return result;
    }

    /**
     * 失败返回（带业务状态码、原因和错误详情）
     * 场景：参数校验失败时返回错误字段详情
     */
    public static <T> Result<T> fail(Integer code, String message, T errorDetails) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setSuccess(false);
        result.setMessage(message);
        result.setData(errorDetails); // data 此时存储错误详情
        return result;
    }
}