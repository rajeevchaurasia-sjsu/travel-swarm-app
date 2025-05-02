package org.sjsu.travelswarm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Column(name = "in_time")
    private LocalDateTime inTime;
    @Column(name = "out_time")
    private LocalDateTime outTime;

    private String location;
    private String price;
    private String notes;

    // Transit details
    @Column(name = "from_location")
    private String fromLocation;
    @Column(name = "transport_mode")
    private String transportMode;
    private String distance;
    private String duration;

    @ManyToOne
    @JoinColumn(name = "itinerary_day_id")
    private ItineraryDay itineraryDay;
}
