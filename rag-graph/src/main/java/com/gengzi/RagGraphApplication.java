package com.gengzi;

import org.springframework.boot.SpringApplication;
import com.gengzi.config.RagGraphBuildProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RagGraphBuildProperties.class)
public class RagGraphApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagGraphApplication.class, args);
    }
}
