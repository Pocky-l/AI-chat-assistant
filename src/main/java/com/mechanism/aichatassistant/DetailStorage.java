package com.mechanism.aichatassistant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory storage for full AI responses.
 * Each response is saved with an auto-incrementing ID.
 * Keeps the last MAX_STORED entries (older ones are evicted automatically).
 */
public class DetailStorage {

    private static final int MAX_STORED = 50;
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private static final LinkedHashMap<Integer, String> STORE = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > MAX_STORED;
        }
    };

    /** Saves a full response and returns its ID. */
    public static int save(String fullText) {
        int id = COUNTER.incrementAndGet();
        STORE.put(id, fullText);
        return id;
    }

    /** Returns the full response by ID, or null if not found / expired. */
    public static String get(int id) {
        return STORE.get(id);
    }
}
