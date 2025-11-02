package com.gengzi.dao.repository;

import com.gengzi.dao.Knowledgebase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface KnowledgebaseRepository extends JpaRepository<Knowledgebase, String>, JpaSpecificationExecutor<Knowledgebase> {
    List<Knowledgebase> findKnowledgebaseByIdIn(Collection<String> ids);
}