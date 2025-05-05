import json # Added for dumping preferences cleanly
from textwrap import dedent # Helper to remove indentation from multi-line strings
from crewai import Task
from .models import FinalItinerary

print("Loading Tasks definition...") # Confirm module load

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

            **Process:**
            1. Delegate sub-tasks to specialist agents...
            2. Analyze specialist results...
            3. Verify & Optimize using tools (check travel times, proximity, feasibility)...
            4. Synthesize all information into the final itinerary structure defined by the 'FinalItinerary' model. Fill in all relevant fields accurately.
        """),
        # Simplified expected output, focusing on content quality
        expected_output=dedent(f"""
                A complete and logically structured travel plan for {duration_calc_str} in {destination}.
                The plan must be practical, considering realistic travel times between activities.
                It must align with the user's budget, interests, and preferences provided in the input.
                All fields in the required output structure should be populated appropriately.
            """),
        agent=manager_agent, # Assign the task to the Manager Agent instance
        output_pydantic = FinalItinerary
    )

    print("Planning task instance created.")
    return planning_task