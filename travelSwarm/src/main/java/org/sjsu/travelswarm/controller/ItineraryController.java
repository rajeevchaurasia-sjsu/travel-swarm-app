package org.sjsu.travelswarm.controller;

import org.sjsu.travelswarm.model.entity.Itinerary;
import org.sjsu.travelswarm.service.ItineraryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ItineraryController {

    @Autowired
    private ItineraryService itineraryService;

    @GetMapping("/plans/{userId}")
    public List<Itinerary> getPlans(@PathVariable String userId) {
        return itineraryService.getItinerariesByUser(userId);
    }
}