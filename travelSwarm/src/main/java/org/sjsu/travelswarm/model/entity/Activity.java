package org.sjsu.travelswarm.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sjsu.travelswarm.model.enums.ActivityType;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ActivityType type;

    private String name;

    @Column(name = "start_time")
    private LocalDateTime startTime;
    @Column(name = "end_time")
    private LocalDateTime endTime;

    private String location;
    private String price;

    @Column(length = 1024)
    private String notes;

    // Transit specific details (populated if type is TRANSPORTATION)
    @Column(name = "from_location")
    private String fromLocation;
    @Column(name = "transport_mode")
    private String transportMode;
    private String distance;
    private String duration;

    @ManyToOne(fetch = FetchType.LAZY) // Added LAZY fetch
    @JoinColumn(name = "itinerary_day_id")
    private ItineraryDay itineraryDay;
}