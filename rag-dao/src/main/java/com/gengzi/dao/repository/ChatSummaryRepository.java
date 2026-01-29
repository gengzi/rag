package com.gengzi.dao.repository;

import com.gengzi.dao.ChatSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 聊天摘要数据访问层
 */
@Repository
public interface ChatSummaryRepository extends JpaRepository<ChatSummary, Long>, JpaSpecificationExecutor<ChatSummary> {

    /**
     * 按会话ID查询所有摘要，按创建时间升序排列
     */
    List<ChatSummary> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    /**
     * 查询会话的最新摘要
     */
    Optional<ChatSummary> findTopByConversationIdOrderByCreatedAtDesc(String conversationId);

    /**
     * 统计会话的摘要数量
     */
    long countByConversationId(String conversationId);
}