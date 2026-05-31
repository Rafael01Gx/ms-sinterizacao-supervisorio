package br.com.sinterpiloto.supervisorio.domain.models;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record PlcSnapshot(
        Instant timestamp,
        Map<String, Object> values
) {
    public PlcSnapshot {
        values = Collections.unmodifiableMap(values);
    }

    public Object get(String tagName) {
        return values.get(tagName);
    }

    public <T> T get(String tagName, Class<T> type) {
        Object val = values.get(tagName);
        if (val == null) return null;
        if (type.isInstance(val)) return type.cast(val);

        if (type == Float.class && val instanceof Number n) return type.cast(n.floatValue());
        if (type == Double.class && val instanceof Number n) return type.cast(n.doubleValue());
        if (type == Integer.class && val instanceof Number n) return type.cast(n.intValue());
        if (type == Boolean.class && val instanceof Number n) return type.cast(n.intValue() != 0);
        return null;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}