package com.gengzi.response;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class AgentGraphRes extends ContentRes{


    private String nodeName;

    private String content;

    private String streamStatus;

    // 下一个节点是否为人类反馈节点
    private Boolean isHumanFeedback;

}
