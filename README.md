# Travel Swarm App

## 🚀 Overview

Travel Swarm is an intelligent travel planning application designed to create personalized itineraries for users. It leverages a Telegram bot for user interaction, a Spring Boot backend for managing conversations and core logic, and a Python-based AI agent service for natural language understanding (NLU) and itinerary generation. This AI service employs an **agentic AI architecture** using a hierarchical team of specialized AI agents (CrewAI) to collaboratively build travel plans. The system uses RabbitMQ for asynchronous communication between the Spring Boot application and the Python agent service, and PostgreSQL for data persistence. All services are containerized using Docker.

## 🏗️ Architecture

The application is built on a **microservices architecture** with a key component being an **agentic AI system** for intelligent planning. These services are orchestrated by Docker Compose.

![Travel Swarm Architecture Diagram](https://github.com/user-attachments/assets/cd2bc52b-b8bb-4208-aeb4-2a99852ec2c0)


**Architectural Layers & Flow:**

1.  **User Interaction Layer (Telegram)**: The user interacts with the system via a Telegram bot.
2.  **Core Application Layer (TravelSwarm Bot Service - Java Spring Boot)**:
    * Receives user messages from the Telegram Bot API.
    * The `ConversationService` manages the dialogue flow, state, and user session.
    * It interacts with the `Natural Language Understanding Client` which makes REST calls to the Python `agent_service` for NLU processing.
    * Based on NLU results, if a comprehensive travel plan is required, it publishes a message to the `Planning Queue` in RabbitMQ via `PlanningRequestPublisher`.
    * The `ItineraryService` handles CRUD operations for itineraries in the PostgreSQL database using JPA.
    * A `PlanningResultListener` consumes completed itineraries (or errors) from the `Result Queue` in RabbitMQ.
3.  **Messaging Layer (RabbitMQ)**:
    * Acts as an asynchronous message broker, decoupling the Spring Boot service from the Python agent service.
    * Manages a `Planning Queue` for new itinerary requests and a `Result Queue` for the generated plans.
4.  **Agentic AI Layer (Travel Swarm Agent Service - Python Flask & CrewAI)**:
    * **NLU Endpoint**: Provides a REST API endpoint (`/parse_request`) for the Spring Boot service to get user intents and entities.
    * **Itinerary Generation**:
        * The `MQ Consumer` listens to the `Planning Queue` for itinerary generation tasks.
        * A `Manager Agent` (using CrewAI) orchestrates a team of specialized AI agents: `Attraction Agent`, `Transport Agent`, `Food Agent`, and `Stay Agent`.
        * These agents use Google Vertex AI models (LLMs) and are equipped with tools to interact with `External Services` (Google Maps API for location/distance, Serper API for web search).
        * The resulting itinerary is published back to the `Result Queue` in RabbitMQ.
5.  **Data Persistence Layer (PostgreSQL)**: Stores all persistent data, including user planning sessions and detailed itineraries.
6.  **Containerization Layer (Docker Environment)**: All services are containerized and managed using Docker and Docker Compose, ensuring consistent deployment and scalability.

## ✨ Features

* **Telegram Bot Interface**: Users interact with the application via a Telegram bot.
* **Natural Language Understanding (NLU)**: Parses user requests in natural language to extract travel parameters like destination, duration, budget, and interests via the Python agent service.
* **Agentic AI Itinerary Generation**: Utilizes a hierarchical team of specialized AI agents (Manager, Attraction, Transport, Food, Stay) built with CrewAI and powered by Google Vertex AI (Gemini Flash model) to collaboratively research, plan, and compile detailed day-by-day travel itineraries.
* **Contextual Conversations**: Maintains conversation context to ask clarifying questions and handle modifications to existing plans.
* **Persistent Storage**: Saves user planning sessions and generated itineraries in a PostgreSQL database using JPA.
* **Asynchronous Task Processing**: Leverages RabbitMQ for decoupling the user-facing application from the potentially long-running itinerary generation process. A `Planning Queue` is used for requests and a `Result Queue` for responses.
* **External API Integration**: AI agents use Google Maps API (for location search, distance/time calculations) and Serper API (for web searches) to gather real-world information for itineraries.
* **View Past Itineraries**: Users can view their previously generated itineraries.

## 🛠️ Technologies Used

* **Frontend/Interface**: Telegram Bot API
* **Backend (Core App - `travelSwarm`)**:
    * Java Spring Boot
    * Spring Data JPA
    * Spring AMQP
    * TelegramBots Java Library
    * Lombok
    * Gradle
* **Agent Orchestration & NLU (`agent_service`)**:
    * Python
    * Flask
    * CrewAI
    * Pika (RabbitMQ client)
    * Pydantic
* **Database**: PostgreSQL
* **Messaging**: RabbitMQ
* **AI/LLM**:
    * Google Vertex AI (Gemini Flash model)
    * LangChain (specifically `langchain-google-vertexai`)
* **External APIs**:
    * Google Maps API
    * Serper API (Web Search)
* **Deployment**: Docker & Docker Compose

## 🔑 Configuration (Environment Variables)

The application relies on several environment variables. These are typically defined in an `.env` file in the project root and sourced by `docker-compose.yml`.

Key environment variables include:

* **Telegram Bot:**
    * `TELEGRAM_BOT_TOKEN`
    * `TELEGRAM_BOT_USERNAME`
* **Database (PostgreSQL):**
    * `DB_URL` (e.g., `jdbc:postgresql://postgres:5432/travelswarm_db`)
    * `DB_USERNAME`
    * `DB_PASSWORD`
    * `POSTGRES_DB` (for PostgreSQL container)
    * `POSTGRES_USER` (for PostgreSQL container)
    * `POSTGRES_PASSWORD` (for PostgreSQL container)
* **RabbitMQ:**
    * `RABBITMQ_HOST` (e.g., `rabbitmq`)
    * `RABBITMQ_PORT` (e.g., `5672`)
    * `RABBITMQ_USER`
    * `RABBITMQ_PASS`
    * `PLANNING_REQUEST_QUEUE` (e.g., `planning_requests`)
    * `RESULTS_QUEUE` (e.g., `results`)
* **Google Cloud & AI Services (for `agent_service`):**
    * `GOOGLE_CLOUD_PROJECT`: Your Google Cloud Project ID.
    * `GOOGLE_API_KEY`: API key for Google Maps.
    * `SERPER_API_KEY`: API key for Serper (web search).
* **Agent Service NLU Endpoint (for Spring Boot service):**
    * This is configured in `travelSwarm/src/main/resources/application.properties` under `agent.service.nlu.url`. It should point to the `agent_service` container and port (e.g., `http://agent_service:5001` if using Docker service names, or the specific IP if configured differently as seen in the example `application.properties`).

Refer to `docker-compose.yml` and `travelSwarm/src/main/resources/application.properties` for a comprehensive list.

## 🚀 Setup & Installation

1.  **Prerequisites**:
    * Docker and Docker Compose installed.
    * Google Cloud Project setup with Vertex AI API enabled.
    * API keys for Google Maps and Serper.
    * Telegram Bot created and token/username obtained.
    * `gcloud` CLI authenticated, `agent_service` relies on it to fetch `GOOGLE_CLOUD_PROJECT`.

2.  **Clone the Repository**:
    ```bash
    git clone git@github.com:rajeevchaurasia-sjsu/travel-swarm-app.git
    cd travel-swarm-app
    ```

3.  **Configure Environment Variables**:
    * Create a `.env` file in the root of the project (alongside `docker-compose.yml`).
    * Populate it with the necessary API keys and configuration values as listed in the "Configuration" section above. Example:
        ```env
        TELEGRAM_BOT_TOKEN=your_telegram_bot_token
        TELEGRAM_BOT_USERNAME=your_telegram_bot_username

        DB_URL=jdbc:postgresql://postgres:5432/travelswarm_db
        DB_USERNAME=root # User for Spring Boot app to connect to DB
        DB_PASSWORD=password # Password for Spring Boot app to connect to DB

        POSTGRES_DB=travelswarm_db # DB name for Postgres container
        POSTGRES_USER=root # Superuser for Postgres container
        POSTGRES_PASSWORD=password # Superuser password for Postgres container

        RABBITMQ_HOST=rabbitmq
        RABBITMQ_PORT=5672
        RABBITMQ_USER=guest
        RABBITMQ_PASS=guest
        PLANNING_REQUEST_QUEUE=planning_requests
        RESULTS_QUEUE=results

        GOOGLE_CLOUD_PROJECT=your-gcp-project-id
        GOOGLE_API_KEY=your_Maps_api_key
        SERPER_API_KEY=your_serper_api_key
        ```
    * Ensure the `agent.service.nlu.url` in `travelSwarm/src/main/resources/application.properties` is correctly set to allow the Spring Boot application to reach the Python `agent_service` (e.g., `http://agent_service:5001/parse_request` if using Docker service discovery).

4.  **Build and Run with Docker Compose**:
    ```bash
    docker-compose up --build -d
    ```
    This command will:
    * Build the Docker images for the `agent_service` and `travel-swarm` service.
    * Pull images for `postgres` and `rabbitmq`.
    * Start all defined services in detached mode.

## ▶️ How to Run / Usage

1.  Ensure all services are running via `docker-compose up`. You can check logs using `docker-compose logs -f`.
2.  Open Telegram and find your bot (using the `TELEGRAM_BOT_USERNAME` you configured).
3.  Start a conversation with the bot:
    * Type `/start` for a welcome message and command list.
    * Type `/new` to initiate a new travel plan.
    * Provide details like "Plan a 5-day trip to Paris with a focus on museums and good food."
4.  The bot will interact, ask clarifying questions if needed, and then generate an itinerary. This may take some time as it involves AI agent processing.
5.  Use `/history` to view past itineraries and `/view <ID>` to see a specific one.

## 🩺 Health Check

The `agent_service` (Python Flask) exposes a health check endpoint:

* `GET /health` (typically on port 5001, e.g., `http://localhost:5001/health` if port 5001 is mapped to host): Returns the status of the LLM initialization and RabbitMQ consumer thread.

## 💡 Future Enhancements

* More sophisticated NLU for complex queries, preferences, and multi-turn refinements.
* User accounts and profiles for personalized defaults and persistent preferences.
* Ability to save/load/share itineraries more robustly (e.g., PDF export, shareable links).
* Integration with booking platforms for flights, hotels, activities.
* Support for multi-destination trips and more complex travel arrangements.
* Real-time updates (e.g., flight delays, weather forecasts affecting the itinerary).
* A web interface as an alternative or supplement to the Telegram bot.
* Enhanced error handling and feedback loops throughout the agent communication.
* More detailed cost estimation and budgeting features.
