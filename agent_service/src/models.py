from pydantic import BaseModel, Field
from typing import List, Optional

# Define structure for a single event within a day
class ItineraryEvent(BaseModel):
    type: str = Field(description="Type of event (e.g., transport, stay, food, attraction)")
    description: str = Field(description="Description of the event")
    startTime: Optional[str] = Field(None, description="Approximate start time (e.g., '9:00 AM')")
    endTime: Optional[str] = Field(None, description="Approximate end time (if applicable)")
    details: Optional[str] = Field(None, description="Additional details (e.g., cost, booking info, address)")
    sourceAgent: Optional[str] = Field(None, description="Which specialist agent provided this info (e.g., FoodAgent)") # Optional tracking

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
    notes: Optional[List[str]] = Field(None, description="Optional list of general travel tips")

print("Pydantic models loaded.")