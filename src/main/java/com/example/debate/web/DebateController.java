package com.example.debate.web;

import com.example.debate.config.DebateProperties;
import com.example.debate.domain.DebateMessage;
import com.example.debate.domain.DebateSession;
import com.example.debate.repository.DebateMessageRepository;
import com.example.debate.repository.DebateSessionRepository;
import com.example.debate.service.DebateOrchestrator;
import com.example.debate.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DebateController {

    private final DebateOrchestrator orchestrator;
    private final SseService sseService;
    private final DebateSessionRepository sessionRepo;
    private final DebateMessageRepository messageRepo;
    private final DebateProperties props;

    @GetMapping("/")
    public String index(Model model) {
        DebateSession running = orchestrator.getCurrentSession();
        if (running != null) {
            List<DebateMessage> messages = messageRepo.findBySessionOrderByCreatedAtAsc(running);
            model.addAttribute("debateSession", running);
            model.addAttribute("messages", messages);
            model.addAttribute("debateRunning", true);
        } else {
            DebateSession last = sessionRepo.findTopByStatusOrderByStartedAtDesc(DebateSession.Status.COMPLETED)
                    .orElse(null);
            if (last != null) {
                List<DebateMessage> messages = messageRepo.findBySessionOrderByCreatedAtAsc(last);
                model.addAttribute("debateSession", last);
                model.addAttribute("messages", messages);
            } else {
                model.addAttribute("messages", Collections.emptyList());
            }
            model.addAttribute("debateRunning", false);
        }
        model.addAttribute("topics", props.getTopics());
        model.addAttribute("ai1Props", props.getAi1());
        model.addAttribute("ai2Props", props.getAi2());
        return "index";
    }

    @PostMapping("/debate/start")
    public String startDebate(@RequestParam String topic) {
        if (orchestrator.isRunning()) {
            log.warn("Debate already running, ignoring start request");
            return "redirect:/";
        }
        if (topic == null || topic.isBlank()) {
            return "redirect:/";
        }
        orchestrator.startDebate(topic.trim());
        return "redirect:/";
    }

    @GetMapping(value = "/debate/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter events() {
        return sseService.subscribe();
    }

    @GetMapping("/debate/history")
    public String history(Model model) {
        List<DebateSession> sessions = sessionRepo.findAll();
        model.addAttribute("sessions", sessions);
        return "history";
    }

    @GetMapping("/debate/history/{id}")
    public String sessionDetail(@PathVariable Long id, Model model) {
        DebateSession debateSession = sessionRepo.findById(id).orElseThrow();
        List<DebateMessage> messages = messageRepo.findBySessionOrderByCreatedAtAsc(debateSession);
        model.addAttribute("debateSession", debateSession);
        model.addAttribute("messages", messages);
        model.addAttribute("debateRunning", false);
        model.addAttribute("topics", props.getTopics());
        model.addAttribute("ai1Props", props.getAi1());
        model.addAttribute("ai2Props", props.getAi2());
        return "index";
    }
}
