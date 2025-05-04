import json # Added for dumping preferences cleanly
from textwrap import dedent # Helper to remove indentation from multi-line strings
from crewai import Task

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
            **Goal:** Create a detailed, optimized, and coherent day-by-day travel itinerary.

            **Inputs:**
            * Destination: {destination}
            * Travel Period: {date_info}
            * Budget Level: {budget}
            * User Interests: {interests_str}
            * User Preferences: {preferences_str}

            **Process:**
            1.  **Delegate:** Identify necessary information (attractions, food, stays, transport) and delegate tasks to specialist agents (Attraction Specialist, Culinary Guide, Accommodation Advisor, Transport Specialist) providing them with relevant criteria based on the user inputs.
            2.  **Analyze Results:** Receive and critically evaluate the suggestions provided by the specialist agents.
            3.  **Verify & Optimize:** Use available tools (Distance/Time, Web Search, Location Search) to:
                * Verify the feasibility of activities (e.g., check opening hours via web search if possible).
                * Estimate travel times between suggested locations for each day using appropriate transport modes.
                * Group activities geographically to minimize unnecessary travel.
                * Ensure suggestions align with budget and interests.
            4.  **Synthesize:** Compile the verified and optimized information into a structured day-by-day itinerary. Ensure a logical flow between activities.
            5.  **Format Output:** Present the final itinerary clearly, following the format specified in 'expected_output'. Include brief descriptions, logistical notes (like travel time estimates), and meal suggestions for each day.
        """),
        expected_output=dedent(f"""
            A complete, optimized, day-by-day itinerary for {duration_calc_str} in {destination}, formatted as a single block of text.
            The itinerary must be practical, considering travel times between locations (verified using tools).
            It must align with the user's specified budget, interests, and preferences.

            **Output Format Example:**

            **Trip Overview:**
            * Destination: {destination}
            * Duration: {duration_calc_str} {('('+start_date+' to '+end_date+')') if start_date and end_date else ''}
            * Budget: {budget}
            * Interests: {interests_str}

            **Day 1: [Theme for Day 1, e.g., Arrival & Historical Center]**
            * **Morning (e.g., 9AM-12PM):**
                * [Activity/Attraction Name]: [Brief Description & Relevance to Interests].
                * *Travel Note:* [e.g., Walk (10 min) from Hotel]
            * **Lunch (e.g., 12:30PM):**
                * [Restaurant Suggestion]: [Type/Description, e.g., Casual Bistro near Attraction].
            * **Afternoon (e.g., 2PM-5PM):**
                * [Activity/Attraction Name]: [Brief Description].
                * *Travel Note:* [e.g., Metro Line X (20 min) from Lunch]
            * **Dinner (e.g., 7PM):**
                * [Restaurant Suggestion]: [Type/Description].
            * **Accommodation:** [Check-in / Name / Area]
            * **Daily Transport Tip:** [e.g., Consider a T-10 Metro pass.]

            **Day 2: [Theme for Day 2]**
            * (Follow similar structure with activities, meals, and travel notes for Morning, Lunch, Afternoon, Dinner)

            **(Repeat structure for all {duration_calc_str})**

            **General Notes:**
            * [Include 1-2 helpful general tips, e.g., booking advice, currency, power adapter type if known.]
        """),
        agent=manager_agent # Assign the task to the Manager Agent instance
    )

    print("Planning task instance created.")
    return planning_task # Return the single, high-level task object