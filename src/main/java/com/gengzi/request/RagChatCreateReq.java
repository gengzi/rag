package com.gengzi.request;

import lombok.Data;

@Data
public class RagChatCreateReq {

    /**
     * 对话名称
     */
    private String chatName;

    /**
     * 知识库id
     */
    private String kbId;


}
