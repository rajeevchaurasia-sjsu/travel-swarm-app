import json
from textwrap import dedent
from crewai import Task
from .models import FinalItinerary

print("Loading Tasks definition...")

def create_planning_task(manager_agent, user_data):
    """Creates the high-level planning task for the manager agent."""

    # Extract user data for embedding in the description and expected output
    destination = user_data.get("destination")
    start_date = user_data.get("startDate")
    end_date = user_data.get("endDate")
    duration_days = user_data.get("duration_days")
    budget = user_data.get("budget", "not specified")
    interests_str = ", ".join(user_data.get("interests", [])) if user_data.get("interests") else "general"
    # Safely format preferences dictionary into a string
    preferences_str = json.dumps(user_data.get("preferences", {}), indent=2)


    # Construct date/duration string for clarity
    duration_calc_str = 'specified' # Fallback
    if start_date and end_date:
        date_info = f"between {start_date} and {end_date}"
        # Calculate duration for output clarity
        try:
            from datetime import datetime
            duration_calc = (datetime.strptime(end_date, '%Y-%m-%d') - datetime.strptime(start_date, '%Y-%m-%d')).days + 1
            date_info += f" (approx. {duration_calc} days)"
            duration_calc_str = f"{duration_calc} days"
        except Exception as e:
            print(f"Date calculation error: {e}") # Log error but continue
            duration_calc_str = f"from {start_date} to {end_date}"
    elif duration_days:
        date_info = f"for a duration of {duration_days} days"
        duration_calc_str = f"{duration_days} days"
    else:
        # This state should be avoided by prior validation in app.py
        date_info = "for an unspecified duration (defaulting to approx 3 days)"
        duration_calc_str = "approx 3 days" # Default if validation failed


    # Define the single high-level task for the manager agent
    planning_task = Task(
        description=dedent(f"""
            **Goal:** Create a detailed, optimized, and coherent day-by-day travel itinerary JSON object.

            **Inputs:**
            * Destination: {destination}
            * Travel Period: {date_info}
            * Budget Level: {budget}
            * User Interests: {interests_str}
            * User Preferences: {preferences_str}

            **Process (Iterative & Hierarchical Steps):**
            Your process involves multiple stages of planning, delegation, and verification to ensure a high-quality itinerary:

            **Phase 1: High-Level Daily Planning & Delegation**
            1.  **Iterate through each day** of the travel period (from {start_date} to {end_date} or for {duration_calc_str}).
            2.  For each day, first determine a **logical theme or primary focus** based on interests and location.
            3.  **Delegate specific information gathering to specialist agents**:
                * **Attraction Agent**: Task it to find relevant attractions for the day's theme/location.
                * **Food Agent**: Task it to find suitable dining options (e.g., breakfast, lunch, dinner) for the day's area and budget.
                * **Stay Agent**: If accommodation is needed for the trip, task it to find suitable options for the entire duration or specific nights. (Note: This might be a one-time delegation, or per-day if multi-city trip).
                * **Transport Agent**: Task it to find transportation options, especially for travel between broad locations or major activities.
            4.  **Crucially, provide precise context and constraints** (e.g., specific dates, timeframes, locations, budget, interests) in your delegation to each specialist.

            **Phase 2: Review, Verification, and Optimization**
            1.  **Analyze Specialist Results**: Once specialists return their findings, critically evaluate their suggestions for relevance, completeness, and feasibility.
            2.  **Verify & Optimize using your tools**:
                * Use `distance_time_tool` to calculate travel times and distances between proposed activities and locations *within each day* to ensure a practical schedule. Adjust timings as needed.
                * Use `web_search_tool` to verify opening hours, check for special events, confirm details, or find missing information for any proposed activities/locations.
                * Use `location_search_tool` to refine ambiguous location queries or find more specific addresses if needed.
            3.  **Identify Gaps/Issues**: If a specialist couldn't find something, or if there's a conflict (e.g., too many activities for a day, illogical travel), **re-delegate or adjust your plan**. You may need to provide a specialist with a revised query or try an alternative approach.
            4.  **Synthesize Daily Plan**: For each day, integrate the verified attractions, food, transport, and accommodation into a coherent, time-boxed schedule. Ensure a logical flow from one event to the next.

            **Phase 3: Final Itinerary Compilation**
            1.  **Synthesize all daily plans** into the overall trip structure.
            2.  **Calculate `estimatedTotalCost`**: Provide a reasonable float number estimate. If detailed costs are not available, use your best judgment based on budget level and activities.
            3.  **Add `general_notes`**: Include overall travel tips or important considerations for the trip.
            4.  **Ensure Strict Adherence to Output Model**: The final output MUST be a JSON object that strictly validates against the 'FinalItinerary' Pydantic model. Pay extremely close attention to the data types for `estimatedTotalCost`, `events.cost`, `interests`, and `general_notes` as specified below.
        """),
        expected_output=dedent(f"""
                            A complete, optimized, day-by-day itinerary for {duration_calc_str} in {destination}.
                            The output MUST be a JSON object that strictly validates against the FinalItinerary Pydantic model.

                            **VERY IMPORTANT TYPE REQUIREMENTS (MANDATORY):**
                            * `estimatedTotalCost`: MUST be a single floating-point number (e.g., 350.0 or 125.50), NOT a string range (like "300-400 USD") or null unless truly unknown after attempting calculation. If you estimate a range, output the average or midpoint as a float.
                            * `events.cost`: THIS FIELD MUST ALWAYS BE A STRING. Examples: "25", "Free", "Varies", "10.00", "$5 (parking)". DO NOT output numbers like 25 or 10.0. Always represent costs as strings, even if they are numerical. Always enclose numerical costs in quotes.
                            * `interests`: MUST be a list of strings (e.g., ["food", "history"]). Use an empty list [] if none.
                            * `general_notes`: MUST be a list of strings (e.g., ["Note 1.", "Note 2."]). Use an empty list [] if none.
                            * Ensure fields like `location`, `website`, `opening_hours`, `bookingInfo` within each event are populated whenever possible based on your research.

                            **Output Format Example (Illustrative - adhere to FinalItinerary model and TYPE REQUIREMENTS):**

                            {{
                                "destination": "{destination}",
                                "duration_days": {duration_days if duration_days else 'null'},
                                "start_date": {json.dumps(start_date)},
                                "end_date": {json.dumps(end_date)},
                                "budget": {json.dumps(budget)},
                                "interests": ["interest1", "interest2"], // MUST be list of strings
                                "summary": "A brief trip summary...",
                                "days": [
                                    {{
                                        "day": 1,
                                        "date": {json.dumps(start_date if start_date else "YYYY-MM-DD or null")},
                                        "theme": "Theme for Day 1",
                                        "events": [
                                            {{
                                                "type": "attraction",
                                                "description": "Visit Place X",
                                                "startTime": "10:00 AM",
                                                "endTime": "12:00 PM",
                                                "location": "123 Main St", // Populate location
                                                "cost": "25.00", // cost MUST be string
                                                "website": "https://example.com", // Populate website
                                                "opening_hours": "10 AM - 5 PM Tue-Sun", // Populate hours
                                                "bookingInfo": "Optional booking info", // Populate booking
                                                "details": "Some details about Place X"
                                            }},
                                            {{
                                                "type": "food",
                                                "description": "Lunch Spot",
                                                "startTime": "12:30 PM",
                                                "endTime": "01:30 PM",
                                                "location": "456 Side St", // Populate location
                                                "cost": "Varies", // cost MUST be string
                                                "website": null, // Example null website
                                                "opening_hours": "11 AM - 10 PM Daily", // Populate hours
                                                "details": "Details..."
                                            }}
                                            // More events
                                        ]
                                    }}
                                    // More days
                                ],
                                "estimatedTotalCost": 500.0, // MUST be float/number
                                "general_notes": ["General tip 1.", "General tip 2."] // MUST be list of strings
                            }}
                        """),
        agent=manager_agent, # Assign the task to the Manager Agent instance
        output_pydantic = FinalItinerary
    )

    print("Planning task instance created.")
    return planning_task