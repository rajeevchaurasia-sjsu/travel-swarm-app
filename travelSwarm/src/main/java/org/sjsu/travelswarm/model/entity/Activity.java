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
    private String cost;
    private String bookingInfo;
    private String website;
    private String notes;

    @Column(length = 1024)
    private String details;

    // Transit specific details (populated if type is TRANSPORTATION)
    @Column(name = "travel_time")
    private String travelTime;
    
    @Column(name = "transport_mode")
    private String transportMode;
    
    private String distance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_day_id")
    private ItineraryDay itineraryDay;

    @Column(name = "opening_hours")
    private String openingHours;
}