import pika
import threading
import time
import json
import os
import traceback

from src.crew import run_hierarchical_travel_crew

# --- RabbitMQ Publisher Helper ---
def publish_message(config, queue_name, message_body, correlation_id=None):
    """Publishes a message to a specified RabbitMQ queue."""
    connection = None
    try:
        print(f"Publisher: Attempting to connect to {config['RABBITMQ_HOST']}...")
        credentials = pika.PlainCredentials(config['RABBITMQ_USER'], config['RABBITMQ_PASS'])
        connection_params = pika.ConnectionParameters(
            host=config['RABBITMQ_HOST'],
            port=config['RABBITMQ_PORT'],
            credentials=credentials
        )
        connection = pika.BlockingConnection(connection_params)
        channel = connection.channel()
        print(f"Publisher: Connected. Declaring queue '{queue_name}'...")
        # Ensure queue exists, make it durable
        channel.queue_declare(queue=queue_name, durable=True)

        # Set message properties (make message persistent, set correlation_id)
        properties = pika.BasicProperties(
            delivery_mode=2, # Make message persistent
            correlation_id=correlation_id
        )

        channel.basic_publish(
            exchange='',
            routing_key=queue_name,
            body=message_body, # Already a JSON string ideally
            properties=properties
        )
        print(f"Publisher: [x] Sent message to queue '{queue_name}'. Correlation ID: {correlation_id}")

    except Exception as e:
        print(f"Publisher: Error publishing message to {queue_name}: {e}")
        traceback.print_exc()
    finally:
        if connection and connection.is_open:
            connection.close()
            print("Publisher: Connection closed.")

# --- RabbitMQ Consumer Callback ---
def planning_request_callback(ch, method, properties, body):
    """Callback function when a message is received from PLANNING_REQUEST_QUEUE."""
    thread_id = threading.get_ident()
    correlation_id = properties.correlation_id # Get correlation ID if sent
    print(f"\n------------------- New Request (CorrID: {correlation_id}) -------------------")
    print(f" [x] Received planning request on thread {thread_id}")
    print(f"     Delivery Tag: {method.delivery_tag}")

    # Load MQ config inside callback to access queue names etc.
    # In a real app, might pass config differently or use a class structure
    mq_config = {
        'RABBITMQ_HOST': os.getenv('RABBITMQ_HOST', 'localhost'),
        'RABBITMQ_PORT': int(os.getenv('RABBITMQ_PORT', 5672)),
        'RABBITMQ_USER': os.getenv('RABBITMQ_USER', 'guest'),
        'RABBITMQ_PASS': os.getenv('RABBITMQ_PASS', 'guest'),
        'RESULTS_QUEUE': os.getenv('RESULTS_QUEUE', 'results'),
        # Add other queues if needed by publish helper, but results is main one now
    }

    message_data = None
    processed_ok = False
    result_message = {"error": "Processing failed"} # Default error message

    try:
        # 1. Decode Message
        message_data = json.loads(body.decode('utf-8'))
        print(f"     Message Body: {json.dumps(message_data, indent=2)}")

        # 2. Basic Validation (can be more robust)
        if not message_data or not isinstance(message_data, dict) or not message_data.get("destination"):
             raise ValueError("Invalid request format or missing 'destination'.")
        if not ((message_data.get("startDate") and message_data.get("endDate")) or message_data.get("duration_days")):
             raise ValueError("Missing required fields: Provide either 'startDate'/'endDate' or 'duration_days'.")
        print("     Input validation passed.")

        # 3. Run CrewAI Logic
        if run_hierarchical_travel_crew:
            print("     Calling run_hierarchical_travel_crew...")
            itinerary_result = run_hierarchical_travel_crew(message_data)
            print("     run_hierarchical_travel_crew finished.")
            # Prepare result message (assuming crew returns string or dict/list)
            if isinstance(itinerary_result, (dict, list)):
                 result_message = itinerary_result
            else:
                 result_message = {"itinerary_suggestion": str(itinerary_result)}
            processed_ok = True
        else:
            raise RuntimeError("Crew execution function not available (failed import/init).")

    except json.JSONDecodeError:
        error_msg = f"Failed to decode JSON: {body}"
        print(f" [!] {error_msg}")
        result_message = {"error": error_msg}
    except ValueError as e:
         error_msg = f"Input validation error: {e}"
         print(f" [!] {error_msg}")
         result_message = {"error": error_msg}
    except Exception as e:
        error_msg = f"Error during CrewAI processing: {e}"
        print(f" [!] {error_msg}")
        traceback.print_exc()
        result_message = {"error": error_msg, "details": traceback.format_exc()}

    # 4. Publish Result (Success or Error)
    try:
        result_json = json.dumps(result_message)
        print(f"     Publishing result to queue '{mq_config['RESULTS_QUEUE']}' (CorrID: {correlation_id})...")
        # Pass necessary config items to publisher
        publish_config = {k: mq_config[k] for k in ['RABBITMQ_HOST', 'RABBITMQ_PORT', 'RABBITMQ_USER', 'RABBITMQ_PASS']}
        publish_message(publish_config, mq_config['RESULTS_QUEUE'], result_json, correlation_id)
    except Exception as pub_e:
        print(f" [!] CRITICAL: Failed to publish result to queue: {pub_e}")
        # Decide how to handle this - maybe try Nacking the original message later?

    # 5. Acknowledge Original Message
    # Acknowledge even if processing failed but we published an error message back
    # Only reject if decoding failed or a truly unrecoverable state occurs where no response can be sent.
    print(f"     Acknowledging original message (tag: {method.delivery_tag})...")
    ch.basic_ack(delivery_tag=method.delivery_tag)
    print(f" [✓] Done handling request (CorrID: {correlation_id}).")
    print(f"------------------------------------------------------------\n")


# --- RabbitMQ Consumer Loop --- (Keep start_consuming as before)
def start_consuming(config):
    """Connects to RabbitMQ and starts consuming messages from a queue."""
    thread_id = threading.get_ident()
    queue_name = config['REQUEST_QUEUE']
    print(f"Consumer thread ({thread_id}) starting for queue '{queue_name}'...")

    credentials = pika.PlainCredentials(config['RABBITMQ_USER'], config['RABBITMQ_PASS'])
    connection_params = pika.ConnectionParameters(
        host=config['RABBITMQ_HOST'],
        port=config['RABBITMQ_PORT'],
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300
    )

    while True:
        connection = None
        try:
            print(f"Consumer ({thread_id}): Attempting connection...")
            connection = pika.BlockingConnection(connection_params)
            channel = connection.channel()
            print(f"Consumer ({thread_id}): Connected.")

            # Declare all queues
            print(f"Consumer ({thread_id}): Declaring queues...")
            channel.queue_declare(queue=config['REQUEST_QUEUE'], durable=True)
            channel.queue_declare(queue=config['RESULTS_QUEUE'], durable=True)
            print(f"Consumer ({thread_id}): Queues declared.")

            print(f"Consumer ({thread_id}): [*] Waiting for messages on queue '{queue_name}'.")
            channel.basic_qos(prefetch_count=1)
            # Use the updated callback
            channel.basic_consume(queue=queue_name, on_message_callback=planning_request_callback)
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            print(f"Consumer ({thread_id}): Connection failed: {e}. Retrying in 5 seconds...")
            if connection and not connection.is_closed: connection.close()
            time.sleep(5)
        except KeyboardInterrupt:
            print(f"Consumer ({thread_id}): KeyboardInterrupt. Closing...")
            if connection and not connection.is_closed: connection.close()
            print(f"Consumer ({thread_id}): Exiting thread.")
            break
        except Exception as e:
            print(f"Consumer ({thread_id}): Unexpected error: {e}. Restarting consumer loop in 10s...")
            traceback.print_exc()
            if connection and not connection.is_closed: connection.close()
            time.sleep(10)