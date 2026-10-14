package com.voiceassistant.model;

/**
 * Values stored in {@link TodoItem#getSyncStatus()}. The names are persisted as strings,
 * so renaming a constant changes what is written to the database.
 */
public enum SyncStatus {
    LOCAL,
    GOOGLE_TASKS_CREATED,
    GOOGLE_TASKS_IMPORTED
}
