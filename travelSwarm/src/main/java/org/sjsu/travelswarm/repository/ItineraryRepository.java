package org.sjsu.travelswarm.repository;

import org.sjsu.travelswarm.model.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    List<Itinerary> findByUserIdOrderByIdDesc(String userId); // Assuming userId is the String chatId
}
