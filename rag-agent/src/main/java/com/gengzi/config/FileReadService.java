package com.gengzi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FileReadService {

    private final ResourceLoader resourceLoader;

    public FileReadService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public InputStream readFromClasspath(String filename) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:" + filename);
        return resource.getInputStream();
    }
}