package com.example.debate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "debate")
public class DebateProperties {

    private String mode = "ollama"; // "ollama" or "external-api"
    private AiProperties ai1 = new AiProperties();
    private AiProperties ai2 = new AiProperties();
    private ExternalApiProperties externalApi = new ExternalApiProperties();
    private int durationMinutes = 60;
    private int maxTokens = 400;
    private double temperature = 0.7;
    private int contextWindow = 10;
    private List<String> topics = List.of("AIは人類の仕事を奪うか");

    @Data
    public static class ExternalApiProperties {
        private String baseUrl = "http://localhost:3000";
    }

    @Data
    public static class AiProperties {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:7b";
        private String name = "AI";
        private String role = "賛成側";
        private String color = "blue";
    }
}
