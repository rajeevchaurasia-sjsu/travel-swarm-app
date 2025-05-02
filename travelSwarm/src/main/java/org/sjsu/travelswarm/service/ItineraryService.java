package org.sjsu.travelswarm.service;

import org.sjsu.travelswarm.dto.ItineraryRequest;
import org.sjsu.travelswarm.model.Itinerary;
import org.sjsu.travelswarm.repository.ItineraryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ItineraryService {

    @Autowired
    private ItineraryRepository itineraryRepository;

    // URL for the external Python FastAPI orchestrator
    private final String PYTHON_URL = "http://localhost:5000/plan";

    /**
     * Generate an itinerary by sending the prompt (ItineraryRequest) to an external service.
     * The orchestrator is expected to return a complete Itinerary object (with days, activities, and stays).
     */
    public Itinerary generateItinerary(ItineraryRequest request) {
        RestTemplate restTemplate = new RestTemplate();

        // Send the request to the orchestrator and get a full Itinerary response.
        Itinerary itineraryResponse = restTemplate.postForObject(PYTHON_URL, request, Itinerary.class);

        // Save the complete itinerary in the database.
        return itineraryRepository.save(itineraryResponse);
    }

    public List<Itinerary> getItinerariesByUser(String userId) {
        return itineraryRepository.findByUserId(userId);
    }
}
