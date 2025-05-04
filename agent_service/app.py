import os
import threading
from flask import Flask, jsonify
from dotenv import load_dotenv

from mq_consumer import start_consuming

print("Loading environment variables...")
load_dotenv()
print("Environment variables loaded.")

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
consumer_thread = threading.Thread(
    target=start_consuming,
    args=(mq_config,), # Pass loaded config
    daemon=True
)
print("Starting consumer thread...")
consumer_thread.start()
# ----------------------------------------------------

@app.route('/health', methods=['GET'])
def health_check():
    """Basic health check endpoint for monitoring."""
    consumer_alive = consumer_thread.is_alive()
    health_status = {
        "status": "OK" if consumer_alive else "ERROR",
        "consumer_thread_alive": consumer_alive
        # Could add a check here to see if MQ connection is active if needed
    }
    status_code = 200 if consumer_alive else 503
    return jsonify(health_status), status_code


if __name__ == '__main__':
    # Running Flask app - primarily for the /health endpoint
    print("Starting Flask app server (host 0.0.0.0, port 5001)...")
    # Disable debug/reloader for stability with threads
    app.run(host='0.0.0.0', port=5001, debug=False, use_reloader=False)