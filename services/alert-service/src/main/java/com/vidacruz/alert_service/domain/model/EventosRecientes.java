package com.vidacruz.alert_service.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class EventosRecientes {

    private final Map<UUID, Boolean> ids;

    public EventosRecientes(int capacidad) {
        if (capacidad <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser positiva");
        }
        this.ids = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                return size() > capacidad;
            }
        };
    }

    public synchronized boolean contiene(UUID eventId) {
        return ids.containsKey(eventId);
    }

    public synchronized void registrar(UUID eventId) {
        ids.put(eventId, Boolean.TRUE);
    }
}
