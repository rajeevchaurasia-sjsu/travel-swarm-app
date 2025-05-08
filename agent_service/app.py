import json
import os
import threading
import traceback
from textwrap import dedent
from flask import Flask, jsonify, request
from dotenv import load_dotenv
from langchain_google_vertexai import VertexAI
from mq_consumer import start_consuming

print("Loading environment variables...")
load_dotenv()
print("Environment variables loaded.")

# --- LLM Initialization ---
llm_instance = None
try:
    project_id = os.getenv('GOOGLE_CLOUD_PROJECT')
    if not project_id:
         # Attempt to get project ID from gcloud config if env var not set
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

    # Using Flash for cost/speed. You could potentially use a different, more powerful model.
    llm_instance = VertexAI(model_name="gemini-2.5-flash-preview-04-17", project=project_id)
    print(f"VertexAI LLM Initialized successfully in app.py for project {project_id}.")

except Exception as e:
    print(f"CRITICAL Error initializing VertexAI LLM in app.py: {e}")
    # Raise exception to prevent app from running without LLM
    raise RuntimeError(f"LLM failed to initialize: {e}") from e
# --------------------

# --- RabbitMQ Configuration Loading ---
# Load all config needed by the consumer thread
mq_config = {
    'RABBITMQ_HOST': os.getenv('RABBITMQ_HOST', 'rabbitmq'),
    'RABBITMQ_PORT': int(os.getenv('RABBITMQ_PORT', 5672)),
    'RABBITMQ_USER': os.getenv('RABBITMQ_USER', 'guest'),
    'RABBITMQ_PASS': os.getenv('RABBITMQ_PASS', 'guest'),
    'REQUEST_QUEUE': os.getenv('PLANNING_REQUEST_QUEUE', 'planning_requests'),
    'RESULTS_QUEUE': os.getenv('RESULTS_QUEUE', 'results'),
}
print(f"MQ Config loaded for consumer thread: Host={mq_config['RABBITMQ_HOST']}, ReqQueue={mq_config['REQUEST_QUEUE']}")
# -----------------------------

app = Flask(__name__)

# --- Start RabbitMQ Consumer in Background Thread ---
print("Creating consumer thread...")
# Make sure llm_instance is initialized before starting thread
if llm_instance:
    consumer_thread = threading.Thread(
        target=start_consuming,
        args=(mq_config, llm_instance),
        daemon=True)
    print("Starting consumer thread...")
    consumer_thread.start()
else:
    print("ERROR: Cannot start consumer thread because LLM failed to initialize.")
    # Set consumer_thread to None or a dummy object so health check reports error
    consumer_thread = threading.Thread() # Create dummy thread object that isn't alive
# ----------------------------------------------------

@app.route('/health', methods=['GET'])
def health_check():
    llm_ok = llm_instance is not None
    consumer_alive = consumer_thread.is_alive()
    status = "OK" if llm_ok and consumer_alive else "ERROR"
    status_code = 200 if status == "OK" else 503
    return jsonify({
        "status": status,
        "llm_initialized": llm_ok,
        "consumer_thread_alive": consumer_alive
    }), status_code

@app.route('/parse_request', methods=['POST'])
def parse_request_endpoint():
    print("\n--- Received request on /parse_request ---")
    global llm_instance
    if not llm_instance:
        print("Error: LLM not initialized, cannot parse request.")
        return jsonify({"error": "LLM not available"}), 503

    if not request.is_json:
        print("Error: Request content type is not JSON")
        return jsonify({"error": "Request must be JSON"}), 400

    data = request.get_json()
    user_text = data.get("userText")

    current_destination = data.get("currentDestination")
    current_duration_days = data.get("currentDurationDays")
    current_start_date = data.get("currentStartDate")
    current_end_date = data.get("currentEndDate")
    current_budget = data.get("currentBudget")
    current_interests = data.get("currentInterests")

    print(f"Raw text to parse: '{user_text}'")
    print(f"Received Context: Dest='{current_destination}', Dur='{current_duration_days}', Start='{current_start_date}', End='{current_end_date}', Budget='{current_budget}', Interests='{current_interests}'")
    # --- End NEW context extraction ---

    if not user_text:
        print("Error: Missing 'userText' in request body")
        return jsonify({"error": "Missing 'userText' in request body"}), 400

    # Define the NLU Prompt
    context_summary = []
    if current_destination: context_summary.append(f"Destination: {current_destination}")
    if current_duration_days: context_summary.append(f"Duration: {current_duration_days} days")
    if current_start_date: context_summary.append(f"Start Date: {current_start_date}")
    if current_end_date: context_summary.append(f"End Date: {current_end_date}")
    if current_budget: context_summary.append(f"Budget: {current_budget}")
    if current_interests: context_summary.append(f"Interests: {', '.join(current_interests)}")

    context_prompt_part = "No previous information gathered yet."
    if context_summary:
        context_prompt_part = "You have already gathered the following information for this trip:\n" + "\n".join(
            context_summary)

    current_status = data.get("currentStatus")  # e.g., "COMPLETED", "STARTED", etc.
    print(f"Received Context Status: '{current_status}'")

    nlu_prompt = dedent(f"""
            You are an NLU system for a travel planning chatbot. Your goal is to understand the user's request, combine it with any previously gathered information, and determine the next step.

            **Previous Context:**
            {context_prompt_part}
            Current Planning Status: {current_status}

            **User's Latest Message:**
            "{user_text}"

            **Instructions:**
            1. Analyze the "User's Latest Message" considering the "Previous Context" and "Current Planning Status".
            2. **If the Current Planning Status is 'COMPLETED' (meaning an itinerary was just shown):**
               a. Determine if the user's message is a request to *modify* the existing itinerary (e.g., "add more nightlife", "change day 1", "find cheaper hotels", "is Alcatraz included?") OR if it's clearly a request for a *completely new trip* (e.g., "now plan a trip to Tokyo").
               b. If it's a modification request, set "status" to "MODIFICATION_REQUEST" and extract the core modification instruction into "modification_details" (e.g., "prefer more clubbing nightlife"). All other fields (destination, duration_days etc.) should reflect the *original* itinerary context. Set "clarification_question" to null.
               c. If it's a request for a *new trip*, ignore the 'COMPLETED' status and proceed as if starting fresh (extract new details, set status to 'COMPLETE' or 'NEEDS_CLARIFICATION' based on the *new* request).
            3. **If the Current Planning Status is NOT 'COMPLETED':**
               a. Combine information from "Previous Context" and "User's Latest Message".
               b. Extract key parameters (destination, duration_days, etc.) based on the combined understanding.
               c. Determine status: 'COMPLETE' if destination AND (duration OR dates) are known, otherwise 'NEEDS_CLARIFICATION'.
               d. If 'NEEDS_CLARIFICATION', formulate 'clarification_question' asking for the *most critical missing piece*. Set "modification_details" to null.
               e. If 'COMPLETE', set 'clarification_question' and 'modification_details' to null.

            **Output Format:**
            Respond ONLY with a valid JSON object containing the following keys, reflecting the *combined and updated* state OR the modification request:
            - "destination": string or null (Reflects current understanding OR original itinerary if modifying).
            - "duration_days": integer or null.
            - "startDate": string (YYYY-MM-DD) or null.
            - "endDate": string (YYYY-MM-DD) or null.
            - "budget": string ("low", "medium", "high", "luxury") or null.
            - "interests": list of strings or null.
            - "status": string ('COMPLETE', 'NEEDS_CLARIFICATION', 'MODIFICATION_REQUEST', or 'ERROR').
            - "clarification_question": string or null (Only if status is 'NEEDS_CLARIFICATION').
            - "modification_details": string or null (Only if status is 'MODIFICATION_REQUEST').

            Strictly adhere to the JSON format. Do not add explanations.
        """)

    try:
        print("Calling LLM for NLU parsing...")
        response = llm_instance.invoke(nlu_prompt)
        print(f"LLM NLU Raw Response: {response}")

        # --- ADD JSON Extraction Logic ---
        json_str = response.strip()  # Remove leading/trailing whitespace first

        # Check if response is wrapped in Markdown code fences (common LLM behavior)
        if json_str.startswith("```json"):
            # Find the end of ```json marker and start after it
            json_str = json_str.split("```json", 1)[1].strip()
            # Remove the trailing ``` if it exists
            if json_str.endswith("```"):
                json_str = json_str[:-3].strip()
        elif json_str.startswith("```"):  # Handle case with just ``` wrapping
            json_str = json_str.split("```", 1)[1].strip()
            if json_str.endswith("```"):
                json_str = json_str[:-3].strip()
        # Sometimes LLMs might just return the JSON without fences,
        # ensure it starts/ends with braces just in case of other minor text
        elif not (json_str.startswith('{') and json_str.endswith('}')):
            # Attempt to find the first '{' and last '}' as a fallback
            start_index = json_str.find('{')
            end_index = json_str.rfind('}')
            if start_index != -1 and end_index != -1 and end_index > start_index:
                json_str = json_str[start_index:end_index + 1].strip()
            else:
                # If we still can't find a JSON structure, raise error early
                raise ValueError("Could not reliably extract JSON object from LLM response.")
        # --- End JSON Extraction Logic ---

        # Attempt to parse the LLM response as JSON
        print(f"Attempting to parse extracted JSON string:\n---\n{json_str}\n---")

        try:
            parsed_result = json.loads(json_str)
            print(f"LLM NLU Parsed JSON: {json.dumps(parsed_result, indent=2)}")
            return jsonify(parsed_result), 200
        except json.JSONDecodeError as json_error:
            # This block now catches errors parsing the *cleaned* string
            print(f"Error: Failed to parse extracted JSON: {json_error}")
            print(f"Cleaned String passed to json.loads: '{json_str}'")
            # Fallback: Ask for rephrasing if LLM fails parsing
            fallback_result = {
                "destination": None, "duration_days": None, "startDate": None, "endDate": None,
                "budget": None, "interests": None, "status": "NEEDS_CLARIFICATION",
                "clarification_question": "Sorry, I had trouble processing the details. Could you please rephrase your request clearly stating the destination and duration or dates?"
            }
            return jsonify(fallback_result), 200  # Return 200 for clarification flow
    except Exception as e:
        print(f"ERROR during NLU LLM call: {e}")
        traceback.print_exc()
        return jsonify({"error": "Failed to parse request using LLM.", "details": str(e)}), 500



if __name__ == '__main__':
    # Running Flask app - primarily for the /health endpoint
    print("Starting Flask app server (host 0.0.0.0, port 5001)...")
    # Disable debug/reloader for stability with threads
    app.run(host='0.0.0.0', port=5001, debug=False, use_reloader=False)