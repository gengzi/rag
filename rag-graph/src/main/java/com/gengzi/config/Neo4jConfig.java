//package com.gengzi.config;
//
//import org.neo4j.driver.Driver;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
//import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
//import org.springframework.transaction.PlatformTransactionManager;
//
//
//@Configuration
//@EnableNeo4jRepositories(basePackages = {"com.gengzi.repository.neo4j"})
//public class Neo4jConfig {
//
//    @Bean("neo4jTransactionManager")
//    public PlatformTransactionManager neo4jTransactionManager(Driver driver) {
//        return new Neo4jTransactionManager(driver);
//    }
//
//}
