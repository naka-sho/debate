package com.example.debate.repository;

import com.example.debate.domain.DebateMessage;
import com.example.debate.domain.DebateSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebateMessageRepository extends JpaRepository<DebateMessage, Long> {

    List<DebateMessage> findBySessionOrderByCreatedAtAsc(DebateSession session);

    List<DebateMessage> findBySessionAndSpeakerOrderByCreatedAtAsc(DebateSession session, String speaker);

    int countBySession(DebateSession session);
}
