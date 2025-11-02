package com.gengzi.dao.repository;

import com.gengzi.dao.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String>, JpaSpecificationExecutor<Conversation> {
    List<Conversation> findByUserId(String id);
}