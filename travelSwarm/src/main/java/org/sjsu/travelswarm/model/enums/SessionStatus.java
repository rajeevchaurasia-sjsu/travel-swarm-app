package org.sjsu.travelswarm.model.enums;

public enum SessionStatus {
    STARTED,                  // Initial state
    WAITING_FOR_CLARIFICATION, // Bot asked user a question
    NLU_COMPLETE,             // NLU successful, ready to send to MQ
    PROCESSING,               // Request sent to MQ, waiting for result
    COMPLETED,                // Result received successfully
    FAILED                    // Processing failed (NLU or Agent error)
}
