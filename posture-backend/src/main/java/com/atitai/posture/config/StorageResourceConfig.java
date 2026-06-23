package com.atitai.posture.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StorageResourceConfig implements WebMvcConfigurer {

    private final PostureProperties properties;

    public StorageResourceConfig(PostureProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(properties.getStorage().getRootDirectory()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        registry.addResourceHandler("/api/v1/posture/storage/**")
            .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}

