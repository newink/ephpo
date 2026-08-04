/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Pool}.
 * @since 0.1.1
 */
final class PoolTest {

    @Test
    @Deprecated
    @SuppressWarnings("deprecation")
    void stillServesTheDeprecatedSharedPool() throws IOException {
        try (
            Reservation reservation = Pool.SINGLETON.acquire();
            ServerSocket server = new ServerSocket(reservation.port())
        ) {
            Assertions.assertEquals(reservation.port(), server.getLocalPort());
        }
    }
}
