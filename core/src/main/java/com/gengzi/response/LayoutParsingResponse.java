package com.gengzi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 版面解析接口顶层响应体
 * 无论请求成功/失败，均返回此结构
 */
@Data
public class LayoutParsingResponse {

    /**
     * 请求的UUID标识
     * 说明：唯一标识一次请求，用于问题排查
     */
    @JsonProperty("logId")
    private String logId;

    /**
     * 错误码
     * 说明：成功时固定为0；失败时与HTTP状态码一致
     */
    @JsonProperty("errorCode")
    private Integer errorCode;

    /**
     * 错误说明
     * 说明：成功时固定为"Success"；失败时返回具体错误信息
     */
    @JsonProperty("errorMsg")
    private String errorMsg;

    /**
     * 操作结果（仅成功时返回）
     * 说明：请求失败时此字段为null
     */
    @JsonProperty("result")
    private LayoutParsingResult result;

    /**
     * 快速判断请求是否成功
     * @return true=成功（errorCode=0且errorMsg=Success），false=失败
     */
    public boolean isSuccess() {
        return errorCode != null && errorCode == 0 && "Success".equals(errorMsg);
    }
}