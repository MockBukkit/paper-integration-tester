package org.mockbukkit.integrationtester.codegen.cheat;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class RegistryCheat<T extends Keyed> implements org.bukkit.Registry<T> {

    Class<T> clazz;

    public RegistryCheat(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public @Nullable T get(@NotNull NamespacedKey namespacedKey) {
        return Mockito.mock(clazz);
    }

    @Override
    public @NotNull T getOrThrow(@NotNull NamespacedKey namespacedKey) {
        return Mockito.mock(clazz);
    }

    @Override
    public @NotNull Stream<T> stream() {
        return Stream.empty();
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return List.of(Mockito.mock(clazz)).iterator();
    }
}
