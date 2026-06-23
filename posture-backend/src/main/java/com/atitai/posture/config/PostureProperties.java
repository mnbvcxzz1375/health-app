package com.atitai.posture.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posture")
public class PostureProperties {

    private final Storage storage = new Storage();
    private final Inference inference = new Inference();
    private final Queue queue = new Queue();
    private final Llm llm = new Llm();

    public Storage getStorage() {
        return storage;
    }

    public Inference getInference() {
        return inference;
    }

    public Queue getQueue() {
        return queue;
    }

    public Llm getLlm() {
        return llm;
    }

    public static class Storage {
        private String rootDirectory = "../runtime-data";

        public String getRootDirectory() {
            return rootDirectory;
        }

        public void setRootDirectory(String rootDirectory) {
            this.rootDirectory = rootDirectory;
        }
    }

    public static class Inference {
        private String baseUrl = "http://localhost:8000";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 600000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Llm {
        private boolean enabled;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";
        private int timeoutMs = 15000;
        private String apiKey;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Queue {
        private String redisKey = "posture:jobs";

        public String getRedisKey() {
            return redisKey;
        }

        public void setRedisKey(String redisKey) {
            this.redisKey = redisKey;
        }
    }
}

