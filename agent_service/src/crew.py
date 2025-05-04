import os
from crewai import Crew, Process
from langchain_google_vertexai import VertexAI

# Import the functions to create agents and tasks from other src modules
from .agents import create_travel_agents
from .tasks import create_planning_task

print("Loading Crew definition module...")

# --- LLM Initialization ---
# Centralized LLM initialization. Handle potential errors.
llm_instance = None
try:
    project_id = os.getenv('GOOGLE_CLOUD_PROJECT')
    if not project_id:
         # Attempt to get project ID from gcloud config if env var not set
         # This might only work if gcloud is accessible in the environment
         try:
              import subprocess
              # Make sure gcloud is in PATH inside the container if relying on this
              project_id = subprocess.check_output(['gcloud', 'config', 'get-value', 'project'], text=True, stderr=subprocess.DEVNULL).strip()
              if project_id:
                  print(f"Using project ID from gcloud config: {project_id}")
              else:
                   raise ValueError("GOOGLE_CLOUD_PROJECT not set and gcloud config project is empty.")
         except FileNotFoundError:
              raise ValueError("'gcloud' command not found. Set GOOGLE_CLOUD_PROJECT environment variable.")
         except Exception as e:
              raise ValueError(f"GOOGLE_CLOUD_PROJECT env var not set and failed to get from gcloud config: {e}")

    # Using Flash for cost/speed. You could potentially use a different, more powerful
    # model (like gemini-1.5-pro) specifically for the manager_llm if needed.
    llm_instance = VertexAI(model_name="gemini-2.0-flash-001", project=project_id)
    print(f"VertexAI LLM Initialized successfully in crew.py for project {project_id}.")

except Exception as e:
    print(f"CRITICAL Error initializing VertexAI LLM in crew.py: {e}")
    # Raise exception to prevent app from running without LLM
    raise RuntimeError(f"LLM failed to initialize: {e}") from e
# --------------------


# --- Main Crew Execution Function ---
def run_hierarchical_travel_crew(user_data):
    """
    Sets up and runs the hierarchical travel planning crew.
    Accepts user data dictionary, returns the final itinerary string.
    """
    if not llm_instance:
        # This check might be redundant if initialization raises RuntimeError, but safe to keep.
        raise RuntimeError("LLM instance is not available. Cannot run crew.")

    # 1. Create Agents (passing the initialized LLM)
    print("Crew Runner: Creating agents...")
    # The create_travel_agents function should return a dictionary of agents
    agents_dict = create_travel_agents(llm_instance)
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
        manager_llm=llm_instance,          # Define LLM for the manager's planning/delegation
        verbose=True                          # Log levels: 0, 1, or 2
        # memory=True # Consider adding memory later for more complex interactions
    )
    print("Crew Runner: Hierarchical crew created.")

    # 4. Kick off the Crew's work
    destination = user_data.get('destination', 'Unknown Destination')
    print(f"Crew Runner: Kicking off crew execution for {destination}...")
    # The hierarchical process handles delegation based on the manager_llm's plan
    crew_result = travel_crew.kickoff()
    print(f"\nCrew Runner: Crew execution finished for {destination}.")

    # Optional: Log the raw output for debugging before returning
    # print(f"Raw Crew Result:\n-------\n{crew_result}\n-------")

    return crew_result # This should be the final synthesized itinerary from the Manager