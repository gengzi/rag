package com.gengzi.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 大纲生成节点
 */
@Component
public class PPTGenerationNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(PPTGenerationNode.class);
    @Autowired
    private AiPPTConfig aiPPTConfig;


    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("开始执行ppt生成");
        String feedback = state.value("human_feedback", "");
        logger.info("用户反馈：{}", feedback);

//        GraphResponse<StreamingOutput> pptGenerationNodeAgentStream = GraphResponse.of(new StreamingOutput("开始生成ppt,请稍等", "pptGenNode", state));
//        GraphResponse<StreamingOutput> pptGenerationNodeAgentStream2 = GraphResponse.of(new StreamingOutput("成功了，请下载查看<a>http://xxx</a>", "pptGenNode", state));
//        GraphResponse<StreamingOutput> pptGenerationNodeAgentStream3 = GraphResponse.of(new StreamingOutput("哈哈", "pptGenNode", state));
        Flux<GraphResponse<StreamingOutput>> map = Flux.just(
                        new StreamingOutput("开始生成ppt,请稍等", "pptGenNode", state),
                        new StreamingOutput("成功了，请下载查看<a>https://www.microsoft.com/zh-cn/microsoft-365/powerpoint</a>", "pptGenNode", state),
                        new StreamingOutput("哈哈", "pptGenNode", state)
                )
                .map(GraphResponse::of);
//        Flux<GraphResponse<StreamingOutput>> pptsGenerationNodeStream =
//                Flux.just(pptGenerationNodeAgentStream, pptGenerationNodeAgentStream2,pptGenerationNodeAgentStream3)
//                        .doOnNext(msg -> logger.info("📤 发送消息: {}", msg.getOutput()));

        return Map.of("PPTGenerationNodeAgentStream", map);

    }
}
