from pydantic import BaseModel, Field
from typing import List, Optional
from pydantic import validator

# Define structure for a single event within a day
class ItineraryEvent(BaseModel):
    type: str = Field(description="Type of event (e.g., transport, stay, food, attraction)")
    description: str = Field(description="Description of the event")
    startTime: Optional[str] = Field(None, description="Approximate start time (e.g., '9:00 AM')")
    endTime: Optional[str] = Field(None, description="Approximate end time (if applicable)")
    details: Optional[str] = Field(None, description="Additional details about the event")
    location: Optional[str] = Field(None, description="Location or address of the event")
    cost: Optional[str] = Field(None, description="Cost information for the event (e.g., '100', 'Free', 'Varies')")
    bookingInfo: Optional[str] = Field(None, description="Booking information or requirements")
    travelTime: Optional[str] = Field(None, description="Travel time for transport events")
    distance: Optional[str] = Field(None, description="Distance for transport events")
    transportMode: Optional[str] = Field(None, description="Mode of transport for transport events")
    website: Optional[str] = Field(None, description="Website URL for the event")
    notes: Optional[str] = Field(None, description="Additional notes about the event")
    opening_hours: Optional[str] = Field(None, description="Opening hours of the venue")

    @validator('cost')
    def convert_cost_to_string(cls, v):
        if v is None:
            return None
        if isinstance(v, (int, float)):
            return str(v)
        return v

    @validator('type')
    def standardize_type(cls, v):
        if v is None:
            return "event"
        v = v.lower()
        # Standardize event types
        type_mapping = {
            "transportation": "transport",
            "meal": "food",
            "accommodation": "stay",
            "museum": "attraction",
            "park": "nature",
            "beach": "nature",
            "nightlife": "entertainment"
        }
        return type_mapping.get(v, v)

    @validator('website')
    def validate_website(cls, v):
        if v is None:
            return None
        if not v.startswith(('http://', 'https://')):
            return f'https://{v}'
        return v

# Define structure for a single day
class ItineraryDay(BaseModel):
    day: int = Field(description="Day number (1, 2, etc.)")
    date: Optional[str] = Field(None, description="Date for the day (YYYY-MM-DD), if available")
    theme: Optional[str] = Field(None, description="A brief theme for the day's activities")
    events: List[ItineraryEvent] = Field(description="List of events scheduled for the day")

# Define the main itinerary structure
class FinalItinerary(BaseModel):
    itineraryId: Optional[str] = Field(None, description="Unique ID for the itinerary")
    destination: str = Field(description="Primary destination city/country")
    duration_days: Optional[int] = Field(None, description="Total duration in days")
    start_date: Optional[str] = Field(None, description="Start date (YYYY-MM-DD)")
    end_date: Optional[str] = Field(None, description="End date (YYYY-MM-DD)")
    budget: Optional[str] = Field(None, description="Budget level (e.g., low, medium, high)")
    interests: Optional[List[str]] = Field(None, description="User interests")
    summary: Optional[str] = Field(None, description="A brief overall summary of the trip")
    days: List[ItineraryDay] = Field(description="List of daily plans")
    estimatedTotalCost: Optional[float] = Field(None, description="Optional overall cost estimate")
    general_notes: Optional[List[str]] = Field(None, description="Optional list of general travel tips")

print("Pydantic models loaded.")