package com.gengzi.response;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AgentGraphRes {


    private String nodeName;

    private String content;

    /**
     * 进行状态
     * 0: 执行中 1: 已完成 2: 失败
     */
    private Integer streamStatus;

    /**
     * 显示的节点名称
     */
    private String displayTitle;


}
