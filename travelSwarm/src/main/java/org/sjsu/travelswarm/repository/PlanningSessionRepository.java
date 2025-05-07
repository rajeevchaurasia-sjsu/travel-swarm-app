package org.sjsu.travelswarm.repository;

import org.sjsu.travelswarm.model.entity.PlanningSession;
import org.sjsu.travelswarm.model.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanningSessionRepository extends JpaRepository<PlanningSession, Long> {

    // Find an ongoing session for a user that might need clarification updated
    Optional<PlanningSession> findFirstByChatIdAndStatusOrderByUpdatedAtDesc(Long chatId, SessionStatus status);

    // Find a session based on the correlation ID when a result comes back from MQ
    Optional<PlanningSession> findByCorrelationId(String correlationId);

    // Find any active (not completed/failed) session for a chat ID
    Optional<PlanningSession> findFirstByChatIdAndStatusInOrderByUpdatedAtDesc(Long chatId, List<SessionStatus> activeStatuses);

}
