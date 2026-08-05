/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A reservation parked in the JUnit store, released when the store closes.
 *
 * <p>JUnit closes stored values through {@code CloseableResource} since 5.1
 * and through {@link AutoCloseable} since 5.13, where the former became
 * deprecated. This object is both, so the port comes back on either
 * engine.</p>
 *
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
final class Parked implements AutoCloseable,
    ExtensionContext.Store.CloseableResource {

    /** Parked reservation. */
    private final Reservation origin;

    /**
     * New parked reservation.
     * @param reservation Reservation to release with the store
     */
    Parked(final Reservation reservation) {
        this.origin = reservation;
    }

    @Override
    public void close() {
        this.origin.close();
    }
}
