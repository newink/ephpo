/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A reservation that holds nothing and remembers being closed.
 * @since 0.1.1
 */
final class FakeReservation implements Reservation {

    /** Port number. */
    private final int value;

    /** Whether this reservation was closed. */
    private final AtomicBoolean shut;

    /**
     * New fake reservation.
     * @param port Port number
     */
    FakeReservation(final int port) {
        this.value = port;
        this.shut = new AtomicBoolean();
    }

    @Override
    public int port() {
        return this.value;
    }

    @Override
    public void close() {
        this.shut.set(true);
    }

    /**
     * Tell whether this reservation was closed.
     * @return Whether it was closed
     */
    boolean closed() {
        return this.shut.get();
    }
}
