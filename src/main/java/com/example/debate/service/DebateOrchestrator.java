package com.example.debate.service;

import com.example.debate.config.DebateProperties;
import com.example.debate.domain.DebateMessage;
import com.example.debate.domain.DebateSession;
import com.example.debate.repository.DebateMessageRepository;
import com.example.debate.repository.DebateSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class DebateOrchestrator {

    private final OllamaChatModel ai1ChatModel;
    private final OllamaChatModel ai2ChatModel;
    private final ExternalApiClient externalApiClient;
    private final DebateSessionRepository sessionRepo;
    private final DebateMessageRepository messageRepo;
    private final SseService sseService;
    private final DebateProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<DebateSession> currentSession = new AtomicReference<>();

    public DebateOrchestrator(
            @Qualifier("ai1ChatModel") OllamaChatModel ai1ChatModel,
            @Qualifier("ai2ChatModel") OllamaChatModel ai2ChatModel,
            ExternalApiClient externalApiClient,
            DebateSessionRepository sessionRepo,
            DebateMessageRepository messageRepo,
            SseService sseService,
            DebateProperties props) {
        this.ai1ChatModel = ai1ChatModel;
        this.ai2ChatModel = ai2ChatModel;
        this.externalApiClient = externalApiClient;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.sseService = sseService;
        this.props = props;
    }

    public boolean isRunning() {
        return running.get();
    }

    public DebateSession getCurrentSession() {
        return currentSession.get();
    }

    @Async("debateExecutor")
    public void startDebate(String topic) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Debate is already running");
            return;
        }

        DebateSession session = createSession(topic);
        currentSession.set(session);

        try {
            runDebateLoop(session);
        } catch (Exception e) {
            log.error("Debate error", e);
            session.setStatus(DebateSession.Status.ERROR);
        } finally {
            session.setEndedAt(LocalDateTime.now());
            session.setStatus(DebateSession.Status.COMPLETED);
            sessionRepo.save(session);
            sseService.broadcastDebateComplete("1時間のディベートが終了しました");
            running.set(false);
            currentSession.set(null);
        }
    }

    private DebateSession createSession(String topic) {
        String modelLabel = isExternalApiMode()
                ? "ext://" + props.getExternalApi().getBaseUrl().replaceFirst("https?://", "") + "/ask"
                : null;

        DebateSession session = new DebateSession();
        session.setTopic(topic);
        session.setAi1Name(props.getAi1().getName());
        session.setAi1Model(modelLabel != null ? modelLabel : props.getAi1().getModel());
        session.setAi1Role(props.getAi1().getRole());
        session.setAi1Color(props.getAi1().getColor());
        session.setAi2Name(props.getAi2().getName());
        session.setAi2Model(modelLabel != null ? modelLabel : props.getAi2().getModel());
        session.setAi2Role(props.getAi2().getRole());
        session.setAi2Color(props.getAi2().getColor());
        session.setStatus(DebateSession.Status.RUNNING);
        session.setStartedAt(LocalDateTime.now());
        DebateSession saved = sessionRepo.save(session);

        sseService.broadcastDebateStart(
                saved.getId(), topic,
                props.getAi1().getName(),
                props.getAi2().getName());
        return saved;
    }

    private void runDebateLoop(DebateSession session) {
        Instant endTime = Instant.now().plus(Duration.ofMinutes(props.getDurationMinutes()));
        int roundNumber = 0;
        List<DebateMessage> history = new ArrayList<>();

        log.info("Debate started: topic={}", session.getTopic());

        while (Instant.now().isBefore(endTime)) {
            // AI1 の番
            DebateMessage msg1 = generateTurn(session, "AI1", roundNumber, history);
            if (msg1 == null) break;
            history.add(msg1);
            roundNumber++;

            if (Instant.now().isAfter(endTime)) break;

            // AI2 の番
            DebateMessage msg2 = generateTurn(session, "AI2", roundNumber, history);
            if (msg2 == null) break;
            history.add(msg2);
            roundNumber++;

            // ステータス更新 (30秒ごとに送信)
            long remaining = Duration.between(Instant.now(), endTime).getSeconds();
            sseService.broadcastStatus(remaining, roundNumber);
        }

        log.info("Debate loop ended after {} rounds", roundNumber);
    }

    private DebateMessage generateTurn(DebateSession session, String speaker,
                                        int roundNumber, List<DebateMessage> history) {
        DebateProperties.AiProperties aiProps = speaker.equals("AI1") ? props.getAi1() : props.getAi2();
        DebateProperties.AiProperties opponentProps = speaker.equals("AI1") ? props.getAi2() : props.getAi1();

        String tempId = UUID.randomUUID().toString();
        sseService.broadcastMessageStart(tempId, speaker, aiProps.getName(), aiProps.getColor(), roundNumber);

        StringBuilder fullContent = new StringBuilder();

        if (isExternalApiMode()) {
            String question = buildQuestion(session, speaker, aiProps, opponentProps, history);
            try {
                String answer = externalApiClient.ask(question);
                fullContent.append(answer);
                sseService.broadcastToken(tempId, answer);
            } catch (Exception e) {
                log.error("Failed to call external API for {}", speaker, e);
                return null;
            }
        } else {
            OllamaChatModel model = speaker.equals("AI1") ? ai1ChatModel : ai2ChatModel;
            List<Message> messages = buildMessages(session, speaker, aiProps, opponentProps, history);
            try {
                model.stream(new Prompt(messages))
                        .doOnNext(response -> {
                            String token = response.getResult().getOutput().getText();
                            if (token != null && !token.isEmpty()) {
                                fullContent.append(token);
                                sseService.broadcastToken(tempId, token);
                            }
                        })
                        .blockLast(Duration.ofMinutes(5));
            } catch (Exception e) {
                log.error("Failed to generate response for {}", speaker, e);
                return null;
            }
        }

        // DBに保存
        DebateMessage message = new DebateMessage();
        message.setSession(session);
        message.setSpeaker(speaker);
        message.setAiName(aiProps.getName());
        message.setAiColor(aiProps.getColor());
        message.setContent(fullContent.toString().trim());
        message.setRoundNumber(roundNumber);
        message.setCreatedAt(LocalDateTime.now());
        DebateMessage saved = messageRepo.save(message);

        sseService.broadcastMessageComplete(tempId, saved.getId(), saved.getContent());
        return saved;
    }

    private List<Message> buildMessages(DebateSession session, String speaker,
                                         DebateProperties.AiProperties aiProps,
                                         DebateProperties.AiProperties opponentProps,
                                         List<DebateMessage> history) {
        List<Message> messages = new ArrayList<>();

        // システムプロンプト
        String systemPrompt = buildSystemPrompt(session.getTopic(), aiProps, opponentProps);
        messages.add(new SystemMessage(systemPrompt));

        // コンテキストウィンドウ内の最近の発言を追加
        int windowSize = props.getContextWindow();
        int start = Math.max(0, history.size() - windowSize);
        List<DebateMessage> recentHistory = history.subList(start, history.size());

        for (DebateMessage msg : recentHistory) {
            if (msg.getSpeaker().equals(speaker)) {
                messages.add(new AssistantMessage(msg.getContent()));
            } else {
                messages.add(new UserMessage(msg.getContent()));
            }
        }

        // 最初の発言の場合
        if (messages.size() == 1) {
            messages.add(new UserMessage("ディベートを開始してください。まず、あなたの立場から冒頭陳述を行ってください。"));
        }

        return messages;
    }

    private boolean isExternalApiMode() {
        return "external-api".equals(props.getMode());
    }

    private String buildQuestion(DebateSession session, String speaker,
                                  DebateProperties.AiProperties aiProps,
                                  DebateProperties.AiProperties opponentProps,
                                  List<DebateMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append(buildSystemPrompt(session.getTopic(), aiProps, opponentProps));

        int windowSize = props.getContextWindow();
        int start = Math.max(0, history.size() - windowSize);
        List<DebateMessage> recentHistory = history.subList(start, history.size());

        if (!recentHistory.isEmpty()) {
            sb.append("\n【これまでの会話】\n");
            for (DebateMessage msg : recentHistory) {
                String name = msg.getSpeaker().equals(speaker) ? aiProps.getName() : opponentProps.getName();
                sb.append(name).append("（").append(
                        msg.getSpeaker().equals(speaker) ? aiProps.getRole() : opponentProps.getRole()
                ).append("）: ").append(msg.getContent()).append("\n");
            }
        } else {
            sb.append("\nディベートを開始してください。まず、あなたの立場から冒頭陳述を行ってください。");
        }

        sb.append("\n次の発言をしてください。");
        return sb.toString();
    }

    private String buildSystemPrompt(String topic, DebateProperties.AiProperties myProps,
                                      DebateProperties.AiProperties opponentProps) {
        return String.format("""
                あなたはディベートの参加者「%s」です。

                ディベートテーマ: 「%s」

                あなたの立場: %s
                対戦相手: %s（%s）

                以下のルールに従ってディベートを行ってください：
                1. 常に%sの立場で論理的に主張してください
                2. 相手の発言に対して具体的に反論し、自分の論拠を補強してください
                3. 1回の発言は150〜300字程度にまとめてください
                4. 感情的にならず、事実や論理に基づいた議論を心がけてください
                5. 日本語で回答してください
                """,
                myProps.getName(), topic, myProps.getRole(),
                opponentProps.getName(), opponentProps.getRole(),
                myProps.getRole());
    }
}
