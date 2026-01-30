package com.gengzi.rag.config;

import io.netty.channel.nio.NioEventLoopGroup;
import org.springframework.context.annotation.Configuration;

@Configuration

public class NettyEventGroup {

    // 自定义 Netty EventLoopGroup（IO 线程池）
    public static final NioEventLoopGroup CUSTOMEVENTLOOPGROUP = new NioEventLoopGroup(10,
            r -> {
                return new Thread(r, "rag-netty-io-thread");
            }
    );
}
