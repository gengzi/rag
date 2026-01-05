package com.gengzi.rag.graph;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RagGraphBuildRunner implements ApplicationRunner {

    private final RagGraphBuildService ragGraphBuildService;
    private final RagGraphBuildProperties properties;

    public RagGraphBuildRunner(RagGraphBuildService ragGraphBuildService, RagGraphBuildProperties properties) {
        this.ragGraphBuildService = ragGraphBuildService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isOnStartup()) {
            return;
        }
        ragGraphBuildService.buildFromIndex(properties.getIndexName(), properties.getBatchSize());
    }
}