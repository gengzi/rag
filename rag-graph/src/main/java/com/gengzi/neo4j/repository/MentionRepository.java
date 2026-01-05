package com.gengzi.neo4j.repository;

import com.gengzi.neo4j.node.Mention;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentionRepository extends Neo4jRepository<Mention, String> {

    // 1. 数据清洗：找出所有置信度低于 0.6 的提及，可能需要删除
    List<Mention> findByConfidenceLessThan(Double confidence);
}