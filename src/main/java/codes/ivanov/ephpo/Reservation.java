/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

/**
 * A port held until this reservation is closed.
 *
 * <p>Closing a reservation more than once is harmless.</p>
 *
 * @since 0.1.0
 */
public interface Reservation extends AutoCloseable {

    /**
     * Obtain the reserved port.
     * @return Port number
     */
    int port();

    @Override
    void close();
}
