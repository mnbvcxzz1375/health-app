package com.atitai.posture.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean(name = "inferenceRestTemplate")
    public RestTemplate restTemplate(RestTemplateBuilder builder, PostureProperties properties) {
        return builder
            .setConnectTimeout(Duration.ofMillis(properties.getInference().getConnectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(properties.getInference().getReadTimeoutMs()))
            .build();
    }

    @Bean(name = "llmRestTemplate")
    public RestTemplate llmRestTemplate(RestTemplateBuilder builder, PostureProperties properties) {
        return builder
            .setConnectTimeout(Duration.ofMillis(properties.getLlm().getTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(properties.getLlm().getTimeoutMs()))
            .build();
    }
}
