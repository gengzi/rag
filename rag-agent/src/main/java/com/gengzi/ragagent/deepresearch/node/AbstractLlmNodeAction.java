package com.gengzi.ragagent.deepresearch.node;

import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;


/**
 * 抽象node节点
 */
public abstract class AbstractLlmNodeAction implements NodeAction {

    abstract ChatClient bulidChatClient();


    abstract PromptTemplate bulidPromptTemplate();


    abstract ToolCallback[] bulidToolCallback();

}
