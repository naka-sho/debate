package com.example.debate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcast(String eventName, Object data) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize SSE data", e);
            return;
        }

        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    public void broadcastToken(String tempId, String token) {
        broadcast("token", Map.of("tempId", tempId, "text", token));
    }

    public void broadcastMessageStart(String tempId, String speaker, String aiName, String aiColor, int roundNumber) {
        broadcast("message-start", Map.of(
                "tempId", tempId,
                "speaker", speaker,
                "aiName", aiName,
                "aiColor", aiColor,
                "roundNumber", roundNumber
        ));
    }

    public void broadcastMessageComplete(String tempId, Long messageId, String content) {
        broadcast("message-complete", Map.of(
                "tempId", tempId,
                "messageId", messageId,
                "content", content
        ));
    }

    public void broadcastDebateStart(Long sessionId, String topic, String ai1Name, String ai2Name) {
        broadcast("debate-start", Map.of(
                "sessionId", sessionId,
                "topic", topic,
                "ai1Name", ai1Name,
                "ai2Name", ai2Name
        ));
    }

    public void broadcastDebateComplete(String reason) {
        broadcast("debate-complete", Map.of("reason", reason));
    }

    public void broadcastStatus(long remainingSeconds, int roundCount) {
        broadcast("status", Map.of(
                "remainingSeconds", remainingSeconds,
                "roundCount", roundCount
        ));
    }

    public int getConnectedCount() {
        return emitters.size();
    }
}
