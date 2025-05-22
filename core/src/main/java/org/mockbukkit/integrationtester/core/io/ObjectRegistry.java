package org.mockbukkit.integrationtester.core.io;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.Map;
import java.util.Optional;

public class ObjectRegistry {

    private final BiMap<Long, Object> objects = HashBiMap.create();
    private final Map<String, Class<?>> classRemapping;

    public ObjectRegistry(Map<String, Class<?>> stringClassMap) {
        this.classRemapping = classRemapping;
    }

    public <T> T getObject(long key) {
        T object = (T) objects.get(key);
        if (object != null) {
            return object;
        }
        throw new IllegalStateException("Unable to find object with key " + key);
    }

    public boolean hasKey(long key) {
        return objects.containsKey(key);
    }

    public Optional<Long> getKey(Object object) {
        return Optional.ofNullable(objects.inverse().get(object));
    }

    public void putObject(Long key, Object object) {
        objects.put(key, object);
    }

    public void clear() {
        objects.clear();
    }

    public boolean hasObject(Object fieldValue) {
        return objects.inverse().containsKey(fieldValue);
    }
}