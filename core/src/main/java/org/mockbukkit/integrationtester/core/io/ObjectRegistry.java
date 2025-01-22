package org.mockbukkit.integrationtester.core.io;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.Optional;

public class ObjectRegistry {

    private final BiMap<Long, Object> objects = HashBiMap.create();

    public <T> T getObject(Long key) {
        T object = (T) objects.get(key);
        if (object != null) {
            return object;
        }
        throw new IllegalStateException("Unable to find object with key " + key);
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
}
