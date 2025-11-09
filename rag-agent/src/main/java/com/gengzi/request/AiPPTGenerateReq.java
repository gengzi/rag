package com.gengzi.request;


import lombok.Data;

@Data
public class AiPPTGenerateReq {


    /**
     * 用户问题
     */
    private String query;

    /**
     * 一次graph执行关联同一个sessionId
     */
    private String sessionId;



}
