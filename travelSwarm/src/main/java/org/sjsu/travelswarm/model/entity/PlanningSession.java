package org.sjsu.travelswarm.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sjsu.travelswarm.model.enums.SessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "planning_session")
@Data
@NoArgsConstructor
public class PlanningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long chatId; // Telegram Chat ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(unique = true, nullable = true) // Should be unique once set
    private String correlationId; // Links MQ request and result

    // --- Store gathered NLU parameters ---
    private String destination;
    private Integer durationDays;
    private String startDate;
    private String endDate;
    private String budget;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> interests;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> preferences;

    private String lastClarificationQuestion; // Store the question asked

    @Column(nullable = true)
    private Long finalItineraryId; // Link to the generated Itinerary entity ID

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Constructor for initial creation
    public PlanningSession(Long chatId) {
        this.chatId = chatId;
        this.status = SessionStatus.STARTED;
    }
}