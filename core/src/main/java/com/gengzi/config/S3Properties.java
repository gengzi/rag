package com.gengzi.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "s3")
@EnableConfigurationProperties(S3Properties.class)
@Data
public class S3Properties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String defaultBucketName;
    private AsyncNettyPool asyncNettyPool;



    // 内部类用于映射async-netty-pool配置
    @Data
    public static class AsyncNettyPool {
        private int coreThreads;
        private int maxThreads;
        private int connectionTimeout;
        private int maxPendingConnectionAcquires;

    }
}
