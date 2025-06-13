from crewai import Agent

from .tools import (
    location_search_tool,
    distance_time_tool,
    web_search_tool
)

print("Loading Agents definitions...")

# function to create agents, accepting the LLM instance
def create_travel_agents(llm):
    """Initializes and returns all travel agents with their tools."""

    # --- Manager Agent ---
    # Responsible for planning, delegating, optimizing, and synthesizing the final output.
    manager_agent = Agent(
        role='Senior Travel Itinerary Architect & Quality Assurance Lead',
        goal=('Design a comprehensive, day-by-day travel itinerary optimized for user requirements (destination, duration/dates, budget, interests, preferences). '
              '**Crucially, you must explicitly delegate distinct sub-tasks to specialist agents (Attraction, Food, Transport, Stay) for EACH day or segment.** '
              'Analyze, critically evaluate, and verify specialist outputs using your tools (e.g., check travel times, proximity, website validity). '
              'Iteratively refine the plan, resolve conflicts, fill gaps, and synthesize all information into a logical, coherent, and highly practical final itinerary. '
              'Ensure the final plan meets all constraints and provides a seamless travel experience.'),
        backstory=('You are a highly experienced and detail-oriented travel architect, renowned for creating perfect itineraries. '
                   'Your strength lies in your ability to break down complex requests, delegate efficiently to your team of specialists, '
                   'and then meticulously review and cross-verify their contributions. You are adept at identifying inconsistencies, '
                   'optimizing logistics (e.g., travel routes and times between activities), and ensuring every detail contributes to an exceptional trip. '
                   'You proactively identify missing information and prompt specialists for clarification or additional searches. Your final output is always robust, verified, and ready for execution.'),
        llm=llm,
        allow_delegation=True, # MANAGER must delegate in a hierarchical process
        tools=[distance_time_tool, web_search_tool, location_search_tool] # Needs tools for optimization, verification, maybe refining locations
    )

    # --- Specialist Agents ---

    attraction_agent = Agent(
        role='Local Attraction Specialist',
        goal=('Based on specific criteria (e.g., location, interests, budget) provided in the task, '
              'find relevant attractions like sightseeing, museums, landmarks, parks, activities, hidden gems, etc. '
              'Provide key details for each (e.g., brief description, relevance, estimated cost/hours if available via search). If no relevant attractions are found for the criteria, clearly state that.'),
        backstory=('You have expert knowledge of attractions worldwide and are skilled at using search tools '
                   'to find places matching specific interests and criteria given to you. You provide concise, factual information and report any limitations clearly.'),
        llm=llm,
        allow_delegation=False, # Specialists execute their delegated tasks
        tools=[location_search_tool, web_search_tool] # Needs tools to find places and details
    )

    transport_agent = Agent(
        role='Transportation Logistics Specialist',
        goal=('Based on specific locations and user preferences provided in the task, '
              'recommend the most suitable transportation options (public transit, walking, ride-sharing, etc.). '
              'Provide practical details like potential costs, travel times (using tools), or pass information found via search. If direct transport is not feasible or difficult, indicate that.'),
        backstory=('You are an expert in urban transportation logistics. You use tools to analyze routes and timings '
                   'to provide the best travel advice for the specific segments requested. You prioritize efficiency and practicality.'),
        llm=llm,
        allow_delegation=False,
        tools=[distance_time_tool, web_search_tool] # Needs tools for times and general info/passes
    )

    food_agent = Agent(
        role='Local Culinary Guide',
        goal=('Based on specific criteria (e.g., location, cuisine, budget, meal type) provided in the task, '
              'identify and recommend specific dining options (restaurants, cafes, markets). '
              'Provide brief context (e.g., cuisine type, price range indication). If no suitable dining options are found, clearly state so.'),
        backstory=('You are a food enthusiast skilled at using search tools to find specific eateries '
                   'matching precise user criteria like location, cuisine type, and budget, as requested. You focus on quality and relevance.'),
        llm=llm,
        allow_delegation=False,
        tools=[location_search_tool, web_search_tool] # Needs tools to find places and details/reviews
    )

    stay_agent = Agent(
        role='Accommodation Advisor',
        goal=('Based on specific criteria (e.g., location, dates/duration, budget, type preference) provided in the task, '
            'find suitable accommodation options. Provide names, types, and potentially relevant neighborhoods or pros/cons. If no suitable options are found, report it clearly.'),
        backstory=('You specialize in finding accommodation matching specific requirements, using search tools '
                   'to identify options based on location, budget, and type requested. You aim for comfort and value.'),
        llm=llm,
        allow_delegation=False,
        tools=[location_search_tool, web_search_tool] # Needs tools to find places and details/availability
    )

    print("Agent instances defined.")
    # Return agents in a dictionary for easy access by name when creating the crew
    return {
        "manager": manager_agent,
        "attraction": attraction_agent,
        "transport": transport_agent,
        "food": food_agent,
        "stay": stay_agent,
    }