# Travel Swarm App

## 🚀 Overview

Travel Swarm is an intelligent travel planning application designed to create personalized itineraries for users. It leverages a Telegram bot for user interaction, a Spring Boot backend for managing conversations and core logic, and a Python-based AI agent service for natural language understanding (NLU) and itinerary generation. This AI service employs an **agentic AI architecture** using a hierarchical team of specialized AI agents (CrewAI) to collaboratively build travel plans. The system uses RabbitMQ for asynchronous communication between the Spring Boot application and the Python agent service, and PostgreSQL for data persistence. All services are containerized using Docker.

## 🏗️ Architecture

The application is built on a **microservices architecture** with a key component being an **agentic AI system** for intelligent planning. These services are orchestrated by Docker Compose.

![Travel Swarm Architecture Diagram](https://github.com/user-attachments/assets/92c5ff86-f490-495a-99a6-fbee99e26d3d)


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
