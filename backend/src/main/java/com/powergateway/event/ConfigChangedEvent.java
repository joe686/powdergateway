package com.powergateway.event;

import org.springframework.context.ApplicationEvent;
import java.util.Map;

public class ConfigChangedEvent extends ApplicationEvent {

    private final Map<String, String> changedEntries;

    public ConfigChangedEvent(Object source, Map<String, String> changedEntries) {
        super(source);
        this.changedEntries = changedEntries;
    }

    public Map<String, String> getChangedEntries() {
        return changedEntries;
    }
}
