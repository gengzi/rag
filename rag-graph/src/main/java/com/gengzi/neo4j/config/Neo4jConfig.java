package com.gengzi.neo4j.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;


@Configuration
@EnableNeo4jRepositories(basePackages = {"com.gengzi.neo4j.repository"})
public class Neo4jConfig {
}
