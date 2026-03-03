package com.example.debate.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "debate_messages")
@Data
@NoArgsConstructor
public class DebateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private DebateSession session;

    // "AI1" or "AI2"
    private String speaker;

    private String aiName;
    private String aiColor;

    @Column(columnDefinition = "TEXT")
    private String content;

    private int roundNumber;

    private LocalDateTime createdAt;
}
