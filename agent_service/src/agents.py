from crewai import Agent
# Import the instantiated tools from our tools module
from .tools import (
    location_search_tool,
    distance_time_tool,
    web_search_tool
    # Import other tools if you define them later
)

print("Loading Agents definitions...") # To confirm module import

# Define a function to create agents, accepting the LLM instance
def create_travel_agents(llm):
    """Initializes and returns all travel agents with their tools."""

    # --- Manager Agent ---
    # Responsible for planning, delegating, optimizing, and synthesizing the final output.
    manager_agent = Agent(
        role='Expert Travel Itinerary Planner & Coordinator',
        goal=('Create a comprehensive, optimized, and coherent day-by-day travel itinerary '
              'based on user requirements (destination, duration/dates, budget, interests, preferences). '
              'Delegate specific tasks (finding attractions, food, stays, transport options) to specialist agents. '
              'Analyze specialist outputs, use tools to check feasibility (like travel times between locations), '
              'and synthesize the results into a logical, final plan.'),
        backstory=('A master planner with extensive knowledge of world travel and logistics. You excel at '
                   'coordinating different pieces of information, optimizing schedules, and creating memorable trips. '
                   'You ensure the final plan is practical and meets all user needs. You critically evaluate suggestions '
                   'and use tools to verify travel times and locations before finalizing the day\'s schedule.'),
        llm=llm,
        verbose=True,
        allow_delegation=True, # MANAGER MUST delegate in hierarchical process
        tools=[distance_time_tool, web_search_tool, location_search_tool] # Needs tools for optimization, verification, maybe refining locations
    )

    # --- Specialist Agents ---

    attraction_agent = Agent(
        role='Local Attraction Specialist',
        goal=('Based on specific criteria (e.g., location, interests, budget) provided in the task, '
              'find relevant attractions like museums, landmarks, parks, hidden gems, etc. '
              'Provide key details for each (e.g., brief description, relevance, estimated cost/hours if available via search).'),
        backstory=('You have expert knowledge of attractions worldwide and are skilled at using search tools '
                   'to find places matching specific interests and criteria given to you.'),
        llm=llm,
        verbose=True,
        allow_delegation=False, # Specialists execute their delegated tasks
        tools=[location_search_tool, web_search_tool] # Needs tools to find places and details
    )

    transport_agent = Agent(
        role='Transportation Logistics Specialist',
        goal=('Based on specific locations and user preferences provided in the task, '
              'recommend the most suitable transportation options (public transit, walking, ride-sharing, etc.). '
              'Provide practical details like potential costs, travel times (using tools), or pass information found via search.'),
        backstory=('You are an expert in urban transportation logistics. You use tools to analyze routes and timings '
                   'to provide the best travel advice for the specific segments requested.'),
        llm=llm,
        verbose=True,
        allow_delegation=False,
        tools=[distance_time_tool, web_search_tool] # Needs tools for times and general info/passes
    )

    food_agent = Agent(
        role='Local Culinary Guide',
        goal=('Based on specific criteria (e.g., location, cuisine, budget, meal type) provided in the task, '
              'identify and recommend specific dining options (restaurants, cafes, markets). '
              'Provide brief context (e.g., cuisine type, price range indication).'),
        backstory=('You are a food enthusiast skilled at using search tools to find specific eateries '
                   'matching precise user criteria like location, cuisine type, and budget, as requested.'),
        llm=llm,
        verbose=True,
        allow_delegation=False,
        tools=[location_search_tool, web_search_tool] # Needs tools to find places and details/reviews
    )

    stay_agent = Agent(
        role='Accommodation Advisor',
        goal=('Based on specific criteria (e.g., location, dates/duration, budget, type preference) provided in the task, '
              'find suitable accommodation options. Provide names, types, and potentially relevant neighborhoods or pros/cons.'),
        backstory=('You specialize in finding accommodation matching specific requirements, using search tools '
                   'to identify options based on location, budget, and type requested.'),
        llm=llm,
        verbose=True,
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