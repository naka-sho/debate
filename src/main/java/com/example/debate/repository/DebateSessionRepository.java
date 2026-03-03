package com.example.debate.repository;

import com.example.debate.domain.DebateSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebateSessionRepository extends JpaRepository<DebateSession, Long> {

    Optional<DebateSession> findTopByStatusOrderByStartedAtDesc(DebateSession.Status status);
}
