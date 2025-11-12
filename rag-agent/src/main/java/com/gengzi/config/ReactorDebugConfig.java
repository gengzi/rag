package com.gengzi.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;



import reactor.core.publisher.Hooks;

@Configuration
@Profile("!prod") // 仅在非生产环境启用（如 dev, test）
public class ReactorDebugConfig {

    @PostConstruct
    public void enableReactorDebug() {
        Hooks.onOperatorDebug();
        // 可选：开启更详细的日志
        // Hooks.onNextDebug(); // 谨慎使用，性能影响大
    }
}