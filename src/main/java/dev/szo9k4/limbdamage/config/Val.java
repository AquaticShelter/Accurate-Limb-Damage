package dev.szo9k4.limbdamage.config;

import java.util.function.Supplier;

/**
 * A read-only live view of one config value. Call sites keep the familiar {@code LimbConfig.X.get()}
 * shape while the data itself now lives in a YACL-managed settings object, so a reload or an edit in
 * the config screen is picked up on the very next read (nothing is cached here).
 */
public final class Val<T> implements Supplier<T> {

    private final Supplier<T> getter;

    public Val(Supplier<T> getter) {
        this.getter = getter;
    }

    @Override
    public T get() {
        return getter.get();
    }
}
