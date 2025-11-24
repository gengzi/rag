package com.gengzi.rag.agent.deepresearch.util;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.gengzi.rag.agent.deepresearch.dto.Plan;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author yingzi
 * @since 2025/6/9
 */

public class StateUtil {

    public static final String EXECUTION_STATUS_ASSIGNED_PREFIX = "assigned_";

    public static final String EXECUTION_STATUS_PROCESSING_PREFIX = "processing_";

    public static final String EXECUTION_STATUS_COMPLETED_PREFIX = "completed_";

    public static final String EXECUTION_STATUS_WAITING_REFLECTING = "waiting_reflecting_";

    public static final String EXECUTION_STATUS_WAITING_PROCESSING = "waiting_processing_";

    public static final String EXECUTION_STATUS_ERROR_PREFIX = "error_";

    public static List<String> getParallelMessages(OverAllState state, List<String> researcherTeam, int count) {
        List<String> resList = new ArrayList<>();

        for (String item : researcherTeam) {
            for (int i = 0; i < count; i++) {
                String nodeName = item + "_content_" + i;
                Optional<String> value = state.value(nodeName, String.class);
                if (value.isPresent()) {
                    resList.add(value.get());
                } else {
                    break;
                }
            }
        }
        return resList;
    }

    public static String getQuery(OverAllState state) {
        return state.value("query", "");
    }

    public static Plan getPlan(OverAllState state) {
        return state.value("currentPlan", new Plan());
    }

    public static String getParalleSearchResult(OverAllState state) {
        AssistantMessage searchResult = state.value("paralleSearchResult", AssistantMessage.builder().build());
        return searchResult.getText();
    }

    public static String getSearchResult(OverAllState state) {
        AssistantMessage searchResult = state.value("searchResult", AssistantMessage.builder().build());
        return searchResult.getText();
    }

    public static String getRagResult(OverAllState state) {
        AssistantMessage ragResult = state.value("ragResult", AssistantMessage.builder().build());
        return ragResult.getText();
    }

    public static String getPlannerResult(OverAllState state) {
        AssistantMessage ragResult = state.value("plannerResult", AssistantMessage.builder().build());
        return ragResult.getText();
    }

    public static String getReporterResult(OverAllState state) {
        AssistantMessage ragResult = state.value("reporterResult", AssistantMessage.builder().build());
        return ragResult.getText();
    }

    public static String getUserId(OverAllState state) {
        return state.value("userId", "");
    }

    public static int getPlanIterations(OverAllState state) {
        return state.value("planIterations", 0);
    }


    public static String getConversationId(OverAllState state) {
        return state.value("conversationId", "");
    }

    public static List<String> getOptimizeQueries(OverAllState state) {
        return state.value("optimize_queries", (List<String>) null);
    }


    public static Integer getMaxStepNum(OverAllState state) {
        return state.value("max_step_num", 3);
    }

    public static Integer getOptimizeQueryNum(OverAllState state) {
        return state.value("optimize_query_num", 3);
    }

    public static String getThreadId(OverAllState state) {
        return state.value("threadId", "");
    }

    public static String getSessionId(OverAllState state) {
        return state.value("session_id", "__default__");
    }

    public static boolean getAutoAcceptedPlan(OverAllState state) {
        return state.value("auto_accepted_plan", true);
    }

    public static String getRagContent(OverAllState state) {
        return state.value("rag_content", "");
    }

    public static boolean isSearchFilter(OverAllState state) {
        return state.value("search_filter", true);
    }

    public static boolean isDeepresearch(OverAllState state) {
        return state.value("enable_deepresearch", true);
    }

}
