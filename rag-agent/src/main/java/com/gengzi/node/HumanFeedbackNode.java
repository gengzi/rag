package com.gengzi.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 人类反馈节点
 */
@Component
public class HumanFeedbackNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(HumanFeedbackNode.class);

    @Override
    public Map<String, Object> apply(OverAllState state) {
        logger.info("用户反馈节点执行");
        HashMap<String, Object> resultMap = new HashMap<>();
        Map<String, Object> feedBackData = state.humanFeedback().data();
        String feedback = (String) feedBackData.getOrDefault("feedback", "");


        String nextStep = "pptGenNode";
        resultMap.put("humannextnode", nextStep);
        resultMap.put("human_feedback", feedback);
        logger.info("humanfeedback node -> {} node", nextStep);
        return resultMap;
    }
}