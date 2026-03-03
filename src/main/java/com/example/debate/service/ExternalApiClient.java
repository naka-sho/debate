package com.example.debate.service;

import com.example.debate.config.DebateProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class ExternalApiClient {

    private final RestClient restClient;

    public ExternalApiClient(DebateProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.getExternalApi().getBaseUrl())
                .build();
    }

    public String ask(String question) {
        log.debug("Calling external API, question length={}", question.length());
        AskResponse response = restClient.post()
                .uri("/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AskRequest(question))
                .retrieve()
                .body(AskResponse.class);
        return response != null ? response.getAnswer() : "";
    }

    record AskRequest(String question) {}

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AskResponse {
        private String answer;
    }
}
