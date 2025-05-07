package org.sjsu.travelswarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sjsu.travelswarm.model.dto.FinalItineraryDto;
import org.sjsu.travelswarm.model.dto.ItineraryDayDto;
import org.sjsu.travelswarm.model.dto.ItineraryEventDto;
import org.sjsu.travelswarm.model.entity.Activity;
import org.sjsu.travelswarm.model.enums.ActivityType;
import org.sjsu.travelswarm.model.entity.Itinerary;
import org.sjsu.travelswarm.model.entity.ItineraryDay;
import org.sjsu.travelswarm.repository.ItineraryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class ItineraryService {

    private final ItineraryRepository itineraryRepository;

    @Transactional
    public Itinerary storeItinerary(FinalItineraryDto dto, String userId) {
        if (dto == null) {
            log.error("Attempted to store a null FinalItineraryDto for user {}", userId);
            throw new IllegalArgumentException("Itinerary DTO cannot be null");
        }
        log.info("Storing itinerary for user {} to destination {}", userId, dto.getDestination());

        Itinerary itineraryEntity = new Itinerary();
        itineraryEntity.setUserId(userId);
        itineraryEntity.setCity(dto.getDestination());
        itineraryEntity.setTripTitle(StringUtils.hasText(dto.getSummary()) ? dto.getSummary() : "Trip to " + dto.getDestination());

        try {
            if (StringUtils.hasText(dto.getStartDate())) {
                itineraryEntity.setStartDate(LocalDate.parse(dto.getStartDate()));
            }
            if (StringUtils.hasText(dto.getEndDate())) {
                itineraryEntity.setEndDate(LocalDate.parse(dto.getEndDate()));
            }
        } catch (DateTimeParseException e) {
            log.warn("Could not parse startDate/endDate from DTO for user {}: {}. Dates will be null.", userId, e.getMessage());
        }
        itineraryEntity.setDurationDays(dto.getDurationDays());
        itineraryEntity.setBudget(dto.getBudget());
        itineraryEntity.setInterests(dto.getInterests() != null ? new ArrayList<>(dto.getInterests()) : new ArrayList<>());
        itineraryEntity.setGeneralNotes(dto.getGeneral_notes() != null ? new ArrayList<>(dto.getGeneral_notes()) : new ArrayList<>());
        itineraryEntity.setEstimatedTotalCost(dto.getEstimatedTotalCost());

        List<ItineraryDay> dayEntities = new ArrayList<>();
        if (dto.getDays() != null) {
            for (ItineraryDayDto dayDto : dto.getDays()) {
                ItineraryDay dayEntity = new ItineraryDay();
                dayEntity.setDayNumber(dayDto.getDay());
                dayEntity.setTheme(dayDto.getTheme());
                try {
                    if (StringUtils.hasText(dayDto.getDate())) {
                        dayEntity.setDate(LocalDate.parse(dayDto.getDate()));
                    }
                } catch (DateTimeParseException e) {
                    log.warn("Could not parse date for day {} for user {}: {}. Date will be null.", dayDto.getDay(), userId, e.getMessage());
                }
                dayEntity.setItinerary(itineraryEntity);

                List<Activity> activityEntities = new ArrayList<>();
                if (dayDto.getEvents() != null) {
                    for (ItineraryEventDto eventDto : dayDto.getEvents()) {
                        Activity activityEntity = mapEventDtoToActivity(eventDto, dayEntity.getDate());
                        activityEntity.setItineraryDay(dayEntity);
                        activityEntities.add(activityEntity);
                    }
                }
                dayEntity.setActivities(activityEntities);
                dayEntities.add(dayEntity);
            }
        }
        itineraryEntity.setDays(dayEntities);
        log.info("Saving Itinerary entity for user {}: {}", userId, itineraryEntity.getTripTitle());
        return itineraryRepository.save(itineraryEntity);
    }

    private Activity mapEventDtoToActivity(ItineraryEventDto eventDto, LocalDate dayDate) {
        Activity activity = new Activity();
        activity.setName(eventDto.getDescription());

        // Time Parsing Logic
        activity.setStartTime(parseToLocalDateTime(eventDto.getStartTime(), dayDate));
        activity.setEndTime(parseToLocalDateTime(eventDto.getEndTime(), dayDate));

        // Type Mapping Logic
        String eventTypeStr = eventDto.getType() != null ? eventDto.getType().trim().toUpperCase(Locale.ROOT) : "OTHER";
        switch (eventTypeStr) {
            case "ATTRACTION": case "SIGHTSEEING":
                activity.setType(ActivityType.ATTRACTION);
                break;
            case "ACTIVITY":
                activity.setType(ActivityType.ACTIVITY);
                break;
            case "MEAL": case "FOOD": case "BREAKFAST": case "LUNCH": case "DINNER": case "CAFE":
                activity.setType(ActivityType.FOOD);
                break;
            case "TRANSIT": case "TRANSPORT": case "TRANSPORTATION":
                activity.setType(ActivityType.TRANSPORTATION);
                break;
            case "STAY": case "HOTEL": case "ACCOMMODATION":
                activity.setType(ActivityType.ACCOMMODATION);
                break;
            default:
                log.warn("Unknown event type string: '{}'. Defaulting to OTHER.", eventDto.getType());
                activity.setType(ActivityType.OTHER);
                break;
        }

        activity.setNotes(eventDto.getDetails());

        if (StringUtils.hasText(eventDto.getDetails()) && eventDto.getDetails().toLowerCase().contains("address:")) {
            try {
                String addressPart = eventDto.getDetails().substring(eventDto.getDetails().toLowerCase().indexOf("address:") + "address:".length()).trim();
                activity.setLocation(addressPart.split("\n")[0]);
            } catch (Exception e) {
                log.warn("Could not parse address from details: {}", eventDto.getDetails());
            }
        }

        log.debug("Mapped eventDto to Activity: {}", activity.getName());
        return activity;
    }

    private LocalDateTime parseToLocalDateTime(String timeStr, LocalDate dayDate) {
        if (!StringUtils.hasText(timeStr) || dayDate == null) {
            return null;
        }
        // Try common time formats
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("h:mm a", Locale.US), // "09:00 AM"
                DateTimeFormatter.ofPattern("HH:mm"),            // "09:00" or "21:00"
                DateTimeFormatter.ISO_LOCAL_TIME                // Standard ISO time
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalTime time = LocalTime.parse(timeStr.trim().toUpperCase(Locale.US), formatter);
                return LocalDateTime.of(dayDate, time);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        log.warn("Could not parse time string '{}' with any known format.", timeStr);
        return null;
    }

    // Method to retrieve itineraries
    public List<Itinerary> getItinerariesByUser(String userId) {
        log.info("Fetching itineraries for user {}", userId);
        return itineraryRepository.findByUserId(userId);
    }
}