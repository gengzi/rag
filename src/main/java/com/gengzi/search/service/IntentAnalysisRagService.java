package com.gengzi.search.service;

/**
 * 意图分析，判断是否追问用户补充细节
 */
public interface IntentAnalysisRagService {


    String intentAnalysis(String question, String conversationId);


}
