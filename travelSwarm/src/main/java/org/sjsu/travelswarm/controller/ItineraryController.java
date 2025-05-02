package org.sjsu.travelswarm.controller;

import jakarta.validation.Valid;
import org.sjsu.travelswarm.dto.ItineraryRequest;
import org.sjsu.travelswarm.model.Itinerary;
import org.sjsu.travelswarm.service.ItineraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ItineraryController {

    @Autowired
    private ItineraryService itineraryService;

    // POST endpoint accepts the lightweight ItineraryRequest (user prompt)
    @PostMapping("/plan")
    public Itinerary createPlan(@Valid @RequestBody ItineraryRequest request) {
        return itineraryService.generateItinerary(request);
    }

    // GET endpoint returns stored itineraries for a given user
    @GetMapping("/plans/{userId}")
    public List<Itinerary> getPlans(@PathVariable String userId) {
        return itineraryService.getItinerariesByUser(userId);
    }
}
