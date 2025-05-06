from crewai import Crew, Process

# Import the functions to create agents and tasks from other src modules
from .agents import create_travel_agents
from .tasks import create_planning_task

print("Loading Crew definition module...")

# --- Main Crew Execution Function ---
def run_hierarchical_travel_crew(user_data, llm):
    """
    Sets up and runs the hierarchical travel planning crew.
    Accepts user data dictionary, returns the final itinerary string.
    """
    if not llm:
        raise RuntimeError("LLM instance is not available. Cannot run crew.")

    # 1. Create Agents (passing the initialized LLM)
    print("Crew Runner: Creating agents...")
    # The create_travel_agents function should return a dictionary of agents
    agents_dict = create_travel_agents(llm)
    manager_agent = agents_dict.get("manager")
    if not manager_agent:
         raise ValueError("Manager agent configuration missing.")
    print(f"Crew Runner: Agents created: {list(agents_dict.keys())}")

    # 2. Create the High-Level Task (passing the manager agent and user data)
    print("Crew Runner: Creating planning task...")
    planning_task = create_planning_task(manager_agent, user_data)
    print("Crew Runner: Planning task created.")

    # 3. Create the Crew with Hierarchical Process
    print("Crew Runner: Creating hierarchical crew...")
    travel_crew = Crew(
        agents=list(agents_dict.values()), # Pass the list of ALL agent instances
        tasks=[planning_task],             # Pass the list containing ONLY the high-level manager task
        process=Process.hierarchical,      # MUST specify the hierarchical process
        manager_llm=llm,          # Define LLM for the manager's planning/delegation
        verbose=True
        # memory=True # Consider adding memory later for more complex interactions
    )
    print("Crew Runner: Hierarchical crew created.")

    # 4. Kick off the Crew's work
    destination = user_data.get('destination', 'Unknown Destination')
    print(f"Crew Runner: Kicking off crew execution for {destination}...")
    # The hierarchical process handles delegation based on the manager_llm's plan
    crew_result = travel_crew.kickoff()
    print(f"\nCrew Runner: Crew execution finished for {destination}.")

    return crew_result