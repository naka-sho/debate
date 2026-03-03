package com.example.debate.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    private final DebateProperties props;

    public OllamaConfig(DebateProperties props) {
        this.props = props;
    }

    @Bean("ai1ChatModel")
    @Primary
    public OllamaChatModel ai1ChatModel() {
        return buildModel(props.getAi1());
    }

    @Bean("ai2ChatModel")
    public OllamaChatModel ai2ChatModel() {
        return buildModel(props.getAi2());
    }

    private OllamaChatModel buildModel(DebateProperties.AiProperties ai) {
        OllamaApi api = OllamaApi.builder()
                .baseUrl(ai.getBaseUrl())
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(ai.getModel())
                .temperature(props.getTemperature())
                .numPredict(props.getMaxTokens())
                .build();
        ModelManagementOptions modelManagement = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.WHEN_MISSING)
                .timeout(Duration.ofMinutes(10))
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .modelManagementOptions(modelManagement)
                .build();
    }
}
