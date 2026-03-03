package com.example.debate.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "debate_sessions")
@Data
@NoArgsConstructor
public class DebateSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String topic;

    private String ai1Name;
    private String ai1Model;
    private String ai1Role;
    private String ai1Color;

    private String ai2Name;
    private String ai2Model;
    private String ai2Role;
    private String ai2Color;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RUNNING;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<DebateMessage> messages = new ArrayList<>();

    public enum Status {
        RUNNING, COMPLETED, ERROR
    }
}
