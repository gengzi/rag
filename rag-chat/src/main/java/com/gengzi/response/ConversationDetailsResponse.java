package com.gengzi.response;

import lombok.Data;

import java.util.List;

@Data
public class ConversationDetailsResponse {

    /**
     * 消息列表
     */
    private List<ChatMessage> message;

//    /**
//     * 是否有更多
//     */
//    private boolean hasMore;

    /**
     * 下一页游标
     */
    private String nextCursor;
}
