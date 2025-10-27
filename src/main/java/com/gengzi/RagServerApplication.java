package com.gengzi;

import com.gengzi.embedding.embed.EmbeddingTool;
import com.gengzi.embedding.load.TextReaderUtils;
import com.gengzi.embedding.split.TextSplitterTool;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;

@SpringBootApplication
@EnableAsync
public class RagServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagServerApplication.class, args);

    }
}