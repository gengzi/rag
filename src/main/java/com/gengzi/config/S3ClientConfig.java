package com.gengzi.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
public class S3ClientConfig {


    @Autowired
    private S3Properties s3Properties;


    @Bean(name = "s3Client")
    public S3AsyncClient createClient() {
        // 构建 Netty HTTP 客户端，注入自定义 EventLoopGroup
        NettyNioAsyncHttpClient nettyHttpClient = (NettyNioAsyncHttpClient) NettyNioAsyncHttpClient.builder()
                .eventLoopGroup(SdkEventLoopGroup.create(NettyEventGroup.CUSTOMEVENTLOOPGROUP)) // 注入自定义线程池
                .connectionTimeout(Duration.ofSeconds(10)) // 连接超时
                .maxConcurrency(500) // 最大并发连接数（默认 100，可根据线程数调整）
                .build();

        return S3AsyncClient.builder()
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                ))
                .serviceConfiguration(service -> service
                        .pathStyleAccessEnabled(true)
                ).httpClient(nettyHttpClient)
                .build();
    }


    @Bean(name = "s3Presigner")
    public S3Presigner createS3Presigner() {
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                ))
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .build();
    }
}
