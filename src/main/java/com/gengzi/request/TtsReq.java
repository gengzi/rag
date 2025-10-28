package com.gengzi.request;

import lombok.Data;

@Data
public class TtsReq {


    /**
     * 某次对话的id
     */
    private String conversationId;

    /**
     * 某次对话中的某次交互的id
     */
    private String chatId;



}
