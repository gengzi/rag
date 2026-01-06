package com.gengzi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;


@Configuration
@EnableNeo4jRepositories(basePackages = {"com.gengzi.repository.neo4j"})
public class Neo4jConfig {
}
