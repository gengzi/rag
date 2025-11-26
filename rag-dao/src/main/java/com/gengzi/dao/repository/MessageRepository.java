package com.gengzi.dao.repository;

import com.gengzi.dao.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long>, JpaSpecificationExecutor<Message> {


    /**
     * 首次加载最近的聊天记录
     *
     * @param conversationId
     * @param limit
     * @return
     */
    @Query("select e from Message e where e.conversation = :conversationId  order by e.createdTime desc ,e.id desc limit :limit")
    List<Message> findMessageByConversationIdAndLimit(String conversationId, int limit);

    /**
     * 首次加载最近的聊天记录
     *
     * @param conversationId
     * @param limit
     * @return
     */
    @Query("select e from Message e where e.conversation = :conversationId  and e.createdTime <= :createdTime and e.id < :msgId  order by e.createdTime desc ,e.id desc limit :limit")
    List<Message> findMessageByConversationIdAndLimitAndNextCursor(String conversationId, int limit, Instant createdTime, Long msgId);


    /**
     * 根据messageid 和 会话id 获取对应的记录
     *
     * @param messageId
     * @param conversationId
     * @return
     */
    @Query("select e from Message e where e.conversation = :conversationId and e.messageId = :messageId ")
    Optional<Message> findMessageByMessageIdAndConversationId(String messageId, String conversationId);

}