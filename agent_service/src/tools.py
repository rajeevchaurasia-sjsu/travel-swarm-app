import os
import json
import traceback

from crewai_tools import BaseTool, SerperDevTool  # Import BaseTool and a pre-built one
import googlemaps

print("Loading Tools...")

# --- Google Maps Client Initialization ---
gmaps_client = None
google_api_key = os.getenv("GOOGLE_API_KEY")
if google_api_key:
    try:
        gmaps_client = googlemaps.Client(key=google_api_key)
        print(f"Google Maps client initialized successfully - {gmaps_client}")
    except Exception as e:
        print(f"Error initializing Google Maps client: {e}. Tools relying on it will fail.")
        gmaps_client = None
else:
    print("Warning: GOOGLE_API_KEY environment variable not found. Google Maps/Places tools will not function.")


# --- Tool: Location Search ---
class LocationSearchTool(BaseTool):
    name: str = "Location Search Tool"
    description: str = ("Searches for specific locations like attractions, restaurants, or hotels "
                        "based on a query (e.g., 'museums in Kyoto', 'restaurants near Eiffel Tower'). "
                        "Input must be a string containing the search query. "
                        "Returns a string list of relevant place names and potentially addresses or types.")
    _gmaps_client: googlemaps.Client | None = None  # Instance variable (private convention)

    def __init__(self):
        super().__init__()
        self._gmaps_client = gmaps_client

    def _run(self, query: str) -> str | None:
        print(f"\n--- TOOL RUNNING: {self.name} ---")
        print(f"Query: {query}")
        if not self._gmaps_client:
            return "Error: Tool not configured due to missing API key."

        try:
            # Use text_search for general queries
            places_result = self._gmaps_client.places(query=query)

            if places_result and places_result.get('status') == 'OK':
                results = places_result.get('results', [])
                if not results:
                    return f"No real places found for query: {query}"

                # Format the output as a string list (adjust as needed for the agent)
                output_list = []
                # Limit results to avoid overwhelming the agent context
                for place in results[:8]:  # Get top 8 results
                    name = place.get('name')
                    address = place.get('formatted_address', 'N/A')
                    output_list.append(f"- {name} (Address: {address})")

                result_str = "\n".join(output_list)
                print(f"Result (found {len(results)} places):\n{result_str}")
                return result_str
            else:
                status = places_result.get('status', 'UNKNOWN_ERROR') if places_result else 'NO_RESPONSE'
                print(f"Error fetching places: Status {status}")
                return f"Error searching for places: Status {status}"

        except Exception as e:
            print(f"Error during Google Places API call: {e}")
            import traceback
            traceback.print_exc()
            return f"Error executing location search tool: {e}"
        finally:
            print(f"--- TOOL END: {self.name} ---\n")


# --- Tool: Distance & Travel Time ---
class DistanceTimeTool(BaseTool):
    name: str = "Distance & Travel Time Tool"
    description: str = ("Calculates the estimated travel distance and time between two specified locations "
                        "using a given mode of transport (walking, transit, driving). "
                        "Input must be a string clearly stating origin, destination, and mode. "
                        "Example: 'Get travel time from Place A to Place B via walking'.")
    _gmaps_client: googlemaps.Client | None = None  # Instance variable (private convention)

    def __init__(self):
        super().__init__()
        self._gmaps_client = gmaps_client

    def _run(self, query: str) -> str | None:
        print(f"\n--- TOOL RUNNING: {self.name} ---")
        print(f"Query: {query}")
        if not self._gmaps_client:
            return "Error: Distance/Time Tool not configured due to missing API key or client initialization failure."

        try:
            origin = None
            destination = None
            mode = "transit"  # Default mode

            # Attempt to parse origin/destination (very basic)
            if " from " in query.lower() and " to " in query.lower():
                parts = query.lower().split(" from ")
                if len(parts) > 1:
                    parts2 = parts[1].split(" to ")
                    if len(parts2) > 1:
                        origin = parts2[0].strip()
                        # Try to extract mode if specified after destination
                        if " via " in parts2[1]:
                            destination = parts2[1].split(" via ")[0].strip()
                            mode_part = parts2[1].split(" via ")[1].strip()
                            if "walk" in mode_part:
                                mode = "walking"
                            elif "drive" in mode_part or "car" in mode_part:
                                mode = "driving"
                            elif "transit" in mode_part or "bus" in mode_part or "metro" in mode_part:
                                mode = "transit"
                        else:
                            destination = parts2[1].strip()
                            # Infer mode if mentioned elsewhere
                            if "walking" in query.lower():
                                mode = "walking"
                            elif "driving" in query.lower():
                                mode = "driving"

            if not origin or not destination:
                return "Error: Could not parse origin and destination from query. Please format as '... from [Origin] to [Destination] via [Mode]'."

            print(f"Calling Distance Matrix API: Origin='{origin}', Destination='{destination}', Mode='{mode}'")
            matrix_result = self._gmaps_client.distance_matrix(origins=[origin], destinations=[destination], mode=mode)

            if matrix_result and matrix_result.get('status') == 'OK':
                element = matrix_result['rows'][0]['elements'][0]
                if element.get('status') == 'OK':
                    distance = element.get('distance', {}).get('text', 'N/A')
                    duration = element.get('duration', {}).get('text', 'N/A')
                    result_str = f"Estimated travel from '{origin}' to '{destination}' via {mode}: Distance: {distance}, Duration: {duration}."
                    print(f"Result: {result_str}")
                    return result_str
                else:
                    element_status = element.get('status', 'UNKNOWN_ELEMENT_ERROR')
                    print(f"Error in Distance Matrix element: Status {element_status}")
                    return f"Error calculating distance/time: Status {element_status}."
            else:
                status = matrix_result.get('status', 'UNKNOWN_ERROR') if matrix_result else 'NO_RESPONSE'
                print(f"Error from Distance Matrix API: Status {status}")
                return f"Error calculating distance/time: API Status {status}."

        except Exception as e:
            print(f"Error during Google Distance Matrix API call: {e}")
            traceback.print_exc()
            return f"Error executing distance/time tool: {e}"
        finally:
            print(f"--- TOOL END: {self.name} ---\n")


# --- Tool: Web Search ---
web_search_tool = None
try:
    serper_api_key = os.getenv("SERPER_API_KEY")
    if serper_api_key:
        print("Attempting to initialize SerperDevTool...")
        web_search_tool = SerperDevTool() # Directly instantiate the pre-built tool
        print("SerperDevTool initialized.")
    else:
        # If key is missing, we'll fall through to the dummy tool creation below
        print("Warning: SERPER_API_KEY not found in environment. Web search will use dummy tool.")
        raise ValueError("Missing Serper Key") # Raise error to trigger fallback
except Exception as e:
    print(f"Failed to initialize SerperDevTool ({e}). Using DummyWebSearchTool.")
    pass # Allow execution to continue to Dummy Tool definition

if not web_search_tool:
    class DummyWebSearchTool(BaseTool): # Define Dummy class ONLY if needed
         name: str = "Web Search Tool"
         description: str = ("Performs a web search for relevant, up-to-date information "
                            "(e.g., opening hours, event schedules, recent reviews). "
                            "Input is the search query string.")
         def _run(self, query: str) -> str:
             print(f"\n--- TOOL RUNNING: {self.name} (DUMMY) ---")
             print(f"Query: {query}")
             result = f"Placeholder: Web search results for '{query}' unavailable (Tool not configured or API Key missing)."
             print(f"Result: {result}")
             print(f"--- TOOL END: {self.name} (DUMMY) ---\n")
             return result
    web_search_tool = DummyWebSearchTool() # Instantiate the dummy tool


# --- Instantiate tools for export ---
# Instantiate AFTER the class definitions
location_search_tool = LocationSearchTool()
distance_time_tool = DistanceTimeTool()
# web_search_tool is instantiated conditionally above

print("Tools loaded.")

# Add more placeholder tools (Weather, Calendar etc.) here if needed
