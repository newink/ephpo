/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link Ports}.
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.CloseResource",
    "PMD.UnitTestContainsTooManyAsserts"
})
final class PortsTest {

    @Test
    void reservesBindablePort() throws IOException {
        try (
            Reservation reservation = new Ports().acquire();
            ServerSocket server = new ServerSocket(reservation.port())
        ) {
            Assertions.assertEquals(
                reservation.port(), server.getLocalPort()
            );
        }
    }

    @Test
    void keepsReservationsDistinct() {
        try (
            Reservation first = new Ports().acquire();
            Reservation second = new Ports().acquire()
        ) {
            Assertions.assertNotEquals(first.port(), second.port());
        }
    }

    @Test
    void givesReservationBackOnClose() {
        final Reservation reservation = new Ports().acquire();
        Assertions.assertEquals(1, Ports.heldCount());
        reservation.close();
        Assertions.assertEquals(0, Ports.heldCount());
        reservation.close();
        Assertions.assertEquals(0, Ports.heldCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"20000", "0-10", "20-10", "1-70000"})
    void rejectsInvalidRanges(final String range) {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new Ports(range, 1L)
        );
    }

    @Test
    void explainsExhaustedRange() throws IOException {
        try (ServerSocket occupied = new ServerSocket(0)) {
            final int port = occupied.getLocalPort();
            final NoFreePortException failure = Assertions.assertThrows(
                NoFreePortException.class,
                () -> new Ports(
                    String.format("%d-%d", port, port), 25L
                ).acquire()
            );
            Assertions.assertTrue(
                failure.getMessage().contains("-Dephpo.range=MIN-MAX"),
                failure::getMessage
            );
        }
    }

    @Test
    void worksAcrossThreads() throws Exception {
        final int threads = 8;
        final ExecutorService service = Executors.newFixedThreadPool(threads);
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Callable<Integer>> tasks = new ArrayList<>(threads);
        for (int idx = 0; idx < threads; ++idx) {
            tasks.add(
                () -> {
                    try (Reservation reservation = new Ports().acquire()) {
                        ready.countDown();
                        start.await();
                        try (
                            ServerSocket server = new ServerSocket(
                                reservation.port()
                            )
                        ) {
                            return server.getLocalPort();
                        }
                    }
                }
            );
        }
        final List<Future<Integer>> results = new ArrayList<>(threads);
        try {
            for (final Callable<Integer> task : tasks) {
                results.add(service.submit(task));
            }
            Assertions.assertTrue(ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            Assertions.assertEquals(
                threads,
                results.stream().map(PortsTest::value).distinct().count()
            );
        } finally {
            start.countDown();
            service.shutdownNow();
        }
    }

    /**
     * Unwrap a completed task.
     * @param future Completed task
     * @return Port number
     */
    private static Integer value(final Future<Integer> future) {
        try {
            return future.get();
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        } catch (final ExecutionException failure) {
            throw new IllegalStateException("Concurrent task failed", failure);
        }
    }
}
