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
    llm_instance = VertexAI(model_name="gemini-2.0-flash-001", project=project_id)
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
    user_text = data.get("user_text")

    if not user_text:
        print("Error: Missing 'user_text' in request body")
        return jsonify({"error": "Missing 'user_text' in request body"}), 400

    print(f"Raw text to parse: '{user_text}'")

    # Define the NLU Prompt
    nlu_prompt = dedent(f"""
        Analyze the following user request for travel planning. Extract the key parameters precisely.
        Respond ONLY with a valid JSON object containing the following keys:
        - "destination": string (City, Country or specific place) or null if not found/ambiguous.
        - "duration_days": integer (Number of days for the trip) or null if not specified or ambiguous. Calculate if possible (e.g., "a week" is 7).
        - "startDate": string (Format YYYY-MM-DD) or null if not specified.
        - "endDate": string (Format YYYY-MM-DD) or null if not specified.
        - "budget": string (Categorize into "low", "medium", "high", "luxury") or null if not mentioned or ambiguous.
        - "interests": list of strings (Keywords representing user interests) or null if none mentioned. Extract specific nouns/activities.
        - "status": string ('COMPLETE' if destination AND (dates OR duration) are found, otherwise 'NEEDS_CLARIFICATION').
        - "clarification_question": string (A question to ask the user if status is 'NEEDS_CLARIFICATION', otherwise null). Ask for the most critical missing information first (destination, then duration/dates).

        Strictly adhere to the JSON format with only the keys listed above. Do not add any explanations or introductory text.

        User Request: "{user_text}"
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