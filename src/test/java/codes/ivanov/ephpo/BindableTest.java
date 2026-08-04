/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Bindable}.
 * @since 0.1.1
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class BindableTest {

    @Test
    void keepsAttemptsOnFreePorts() throws IOException {
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final FakeReservation reservation = new FakeReservation(port);
        Assertions.assertEquals(
            Optional.of(reservation),
            new Bindable(() -> Optional.of(reservation)).made()
        );
        Assertions.assertFalse(reservation.closed());
    }

    @Test
    void givesOccupiedAttemptsBack() throws IOException {
        try (ServerSocket occupied = new ServerSocket(0)) {
            final FakeReservation reservation = new FakeReservation(
                occupied.getLocalPort()
            );
            Assertions.assertEquals(
                Optional.empty(),
                new Bindable(() -> Optional.of(reservation)).made()
            );
            Assertions.assertTrue(reservation.closed());
        }
    }

    @Test
    void skipsInterfacesThisHostCannotResolve() throws IOException {
        try (ServerSocket occupied = new ServerSocket(0)) {
            final FakeReservation reservation = new FakeReservation(
                occupied.getLocalPort()
            );
            Assertions.assertEquals(
                Optional.of(reservation),
                new Bindable(
                    () -> Optional.of(reservation), "ephpo invalid host"
                ).made()
            );
            Assertions.assertFalse(reservation.closed());
        }
    }

    @Test
    void passesEmptyAttemptsThrough() {
        Assertions.assertEquals(
            Optional.empty(), new Bindable(Optional::empty).made()
        );
    }
}
