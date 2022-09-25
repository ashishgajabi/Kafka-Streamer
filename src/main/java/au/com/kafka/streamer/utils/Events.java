package au.com.kafka.streamer.utils;

public enum Events {
    BindingRestartFailure("BindingRestartFailureEvent"),
    SerializationFailure("SerializationFailureEvent"),
    DeserializationFailure("DeserializationFailureEvent"),
    UncompressionFailure("UncompressionFailureEvent"),
    PublishFailure("PublishFailureEvent");
    
    private String eventName;
    
    Events(String eventName) { this.eventName = eventName; }

    @Override
    public String toString() {
        return eventName;
    }
}
