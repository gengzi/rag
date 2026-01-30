package com.gengzi.rag.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * DuckDB 数据源配置
 */
@Configuration
public class DuckDbConfig {
    @Bean
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary // 关键点：标记为主数据源，JPA 会自动认领这个
    public DataSource dataSource() {
        return primaryDataSourceProperties().initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
    @Bean
    @Primary // <--- 关键：默认的 JdbcTemplate 使用主库
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "duckdbDataSource")
    public DataSource duckdbDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setJdbcUrl("jdbc:duckdb:");
        dataSource.setPoolName("DuckDBPool");
        // DuckDB in-memory is fast, but we can set some pool settings suitable for
        // local tasks
        dataSource.setMaximumPoolSize(10);
        return dataSource;
    }

    /**
     * 配置 DuckDB JdbcTemplate
     */
    @Bean(name = "duckdbJdbcTemplate")
    public JdbcTemplate duckdbJdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(duckdbDataSource());
        // 设置查询超时时间（秒）
        jdbcTemplate.setQueryTimeout(300);
        return jdbcTemplate;
    }
}
