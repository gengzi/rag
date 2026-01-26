//package com.gengzi.config;
//
//import com.zaxxer.hikari.HikariDataSource;
//import org.springframework.boot.context.properties.ConfigurationProperties;
//import org.springframework.boot.jdbc.DataSourceBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.jdbc.core.JdbcTemplate;
//
//import javax.sql.DataSource;
//
///**
// * DuckDB 数据源配置
// */
//@Configuration
//public class DuckDbConfig {
//
//    /**
//     * 1. 创建 DuckDB 数据源 Bean
//     * 读取 application.yml 中 spring.duckdb.datasource 前缀的配置
//     */
//    @Bean(name = "duckDbDataSource")
//    @ConfigurationProperties(prefix = "spring.duckdb.datasource")
//    public DataSource duckDbDataSource() {
//        // 使用 HikariCP 连接池 (Spring Boot 默认)
//        return DataSourceBuilder.create().type(HikariDataSource.class).build();
//    }
//
//    /**
//     * 配置 DuckDB JdbcTemplate
//     */
//    @Bean(name = "duckdbJdbcTemplate")
//    public JdbcTemplate duckdbJdbcTemplate() {
//        JdbcTemplate jdbcTemplate = new JdbcTemplate(duckDbDataSource());
//        // 设置查询超时时间（秒）
//        jdbcTemplate.setQueryTimeout(300);
//        return jdbcTemplate;
//    }
//}
