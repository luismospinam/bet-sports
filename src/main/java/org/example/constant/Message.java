package org.example.constant;

public enum Message {
    EXISTING_EVENT_CHANGE_ODD("Event with id %d named %s changed odds from %f to %f for Line %f and event_type %s");

    private final String message;

    Message(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
