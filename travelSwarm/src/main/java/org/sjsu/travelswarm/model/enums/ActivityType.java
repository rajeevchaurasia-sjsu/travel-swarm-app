package org.sjsu.travelswarm.model.enums;

public enum ActivityType {
    ATTRACTION,      // For visiting landmarks, museums, parks, points of interest
    ACTIVITY,        // For other general scheduled activities (e.g., "Relax at cafe", "Shopping")
    FOOD,            // For specific meal events (breakfast, lunch, dinner, snacks)
    TRANSPORTATION,  // For travel segments between locations (metro, bus, walking, taxi)
    ACCOMMODATION,   // For events related to lodging (e.g., "Check into Hotel", "Hotel details")
    OTHER
}
