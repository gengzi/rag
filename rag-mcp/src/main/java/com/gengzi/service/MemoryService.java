package com.gengzi.service;

public interface MemoryService {

    /**
     * 查询用户历史记忆
     *
     *
     * @param userId
     * @param query
     * @return
     */
    String searchMemory(String userId, String query);


    /**
     * 存储用户记忆
     * @param userId
     * @param content
     * @return
     */
    String storeMemory(String userId, String content);
}
