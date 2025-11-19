package com.gengzi.dao.repository;

import com.gengzi.dao.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

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
}