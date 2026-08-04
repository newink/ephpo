/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Injects {@link Ephemeral} parameters and fields for one test invocation.
 * @since 0.1.0
 */
public final class EphemeralResolver implements ParameterResolver,
    BeforeEachCallback {

    /** Reservation store namespace. */
    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(EphemeralResolver.class);

    /** Unique store key source. */
    private static final AtomicLong KEYS = new AtomicLong();

    /** Port pool. */
    private final Pool pool;

    /**
     * New resolver backed by the shared pool.
     */
    public EphemeralResolver() {
        this(Pool.SINGLETON);
    }

    /**
     * New resolver backed by a supplied pool.
     * @param origin Port pool
     */
    EphemeralResolver(final Pool origin) {
        this.pool = origin;
    }

    @Override
    public boolean supportsParameter(final ParameterContext context,
        final ExtensionContext ignored) {
        return context.isAnnotated(Ephemeral.class);
    }

    @Override
    public Object resolveParameter(final ParameterContext context,
        final ExtensionContext extension) {
        final Class<?> type = context.getParameter().getType();
        if (!EphemeralResolver.supported(type)) {
            throw new IllegalArgumentException(
                String.format(
                    String.join(
                        "", "@Ephemeral parameter #%d of \"%s\" is %s, ",
                        "must be int or Integer"
                    ),
                    context.getIndex(),
                    context.getDeclaringExecutable().getName(),
                    type.getName()
                )
            );
        }
        return this.park(extension);
    }

    @Override
    public void beforeEach(final ExtensionContext extension) {
        final Object test = extension.getRequiredTestInstance();
        Class<?> type = test.getClass();
        while (!type.equals(Object.class)) {
            for (final Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Ephemeral.class)) {
                    this.inject(test, field, extension);
                }
            }
            type = type.getSuperclass();
        }
    }

    /**
     * Park a reservation in the invocation-scoped JUnit store.
     * @param extension Current extension context
     * @return Reserved port
     */
    private Integer park(final ExtensionContext extension) {
        final Reservation reservation = this.pool.acquire();
        extension.getStore(EphemeralResolver.NAMESPACE).put(
            EphemeralResolver.KEYS.incrementAndGet(),
            (ExtensionContext.Store.CloseableResource) reservation::close
        );
        return reservation.port();
    }

    /**
     * Assign an annotated field.
     * @param test Test instance
     * @param field Annotated field
     * @param extension Current extension context
     */
    private void inject(final Object test, final Field field,
        final ExtensionContext extension) {
        if (extension.getExecutionMode().equals(ExecutionMode.CONCURRENT)
            && extension.getTestInstanceLifecycle().orElse(
                TestInstance.Lifecycle.PER_METHOD
            ).equals(TestInstance.Lifecycle.PER_CLASS)) {
            throw new ExtensionConfigurationException(
                String.format(
                    String.join(
                        "", "@Ephemeral field \"%s\" cannot be injected ",
                        "into a concurrent PER_CLASS test instance; use ",
                        "parameter injection, PER_METHOD, or ",
                        "@Execution(SAME_THREAD)"
                    ),
                    field.getName()
                )
            );
        }
        if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException(
                String.format(
                    String.join(
                        "", "@Ephemeral field \"%s\" is static; ",
                        "@Ephemeral supports instance fields only"
                    ),
                    field.getName()
                )
            );
        }
        if (!EphemeralResolver.supported(field.getType())) {
            throw new IllegalArgumentException(
                String.format(
                    "@Ephemeral field \"%s\" is %s, must be int or Integer",
                    field.getName(), field.getType().getName()
                )
            );
        }
        try {
            final VarHandle handle = MethodHandles.privateLookupIn(
                field.getDeclaringClass(), MethodHandles.lookup()
            ).unreflectVarHandle(field);
            handle.set(test, this.park(extension));
        } catch (final IllegalAccessException failure) {
            throw new IllegalStateException(
                String.format(
                    "Failed to assign @Ephemeral field \"%s\"",
                    field.getName()
                ),
                failure
            );
        }
    }

    /**
     * Check whether a type can receive a port number.
     * @param type Candidate type
     * @return Whether it is supported
     */
    private static boolean supported(final Class<?> type) {
        return type.equals(Integer.TYPE) || type.equals(Integer.class);
    }
}
