package com.gengzi.response;


import lombok.Data;

@Data
public class AgentInfo {


    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 节点返回的内容信息
     */
    private String content;


    /**
     * 节点标题
     */
    private String displayTitle;


    /**
     * 返回当前的任务id
     */
    private String sessionId;


    private String fileType;

    private String fileName;

    private String filePath;


}
