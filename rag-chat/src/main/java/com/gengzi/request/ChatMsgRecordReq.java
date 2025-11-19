package com.gengzi.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 聊天对话请求类
 */
@Data
public class ChatMsgRecordReq {

    @Max(value = 50)
    @Min(value = 5)
    private int limit;

    /**
     * 之前已经加载的聊天记录start节点
     * 游标：格式为 {created_at}_{message_id}，如 "2025-06-01T10:00:00_5000"
     */
    private String before;


}
