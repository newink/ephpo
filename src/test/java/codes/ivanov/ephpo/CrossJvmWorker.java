/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Child-process entry point for cross-JVM integration tests.
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.CloseResource",
    "PMD.LongVariable",
    "PMD.UnnecessaryLocalRule",
    "PMD.UnusedLocalVariable"
})
public final class CrossJvmWorker {

    /** Diagnostic logger. */
    private static final System.Logger LOG = System.getLogger(
        CrossJvmWorker.class.getName()
    );

    /** Maximum sockets held by one churn process. */
    private static final int CHURN_BATCH = 400;

    /** Coordination wait timeout. */
    private static final long COORDINATION_TIMEOUT = 10_000L;

    /** Utility class. */
    private CrossJvmWorker() {
        // Intentionally empty.
    }

    /**
     * Run one worker mode.
     * @param args Mode and its arguments
     * @throws Exception When the worker cannot complete
     */
    public static void main(final String... args) throws Exception {
        final String mode = args[0];
        if ("churn".equals(mode)) {
            CrossJvmWorker.churn(Long.parseLong(args[1]), Path.of(args[2]));
        } else if ("naive".equals(mode)) {
            CrossJvmWorker.naive(
                Path.of(args[1]), Integer.parseInt(args[2]),
                Long.parseLong(args[3])
            );
        } else if ("owner".equals(mode)) {
            CrossJvmWorker.owner(
                Path.of(args[1]), Path.of(args[2]), Path.of(args[3])
            );
        } else if ("thief".equals(mode)) {
            CrossJvmWorker.thief(
                Path.of(args[1]), Path.of(args[2]), Long.parseLong(args[3])
            );
        } else if ("pool".equals(mode)) {
            CrossJvmWorker.pool(args);
        } else if ("hold".equals(mode)) {
            CrossJvmWorker.hold(
                Path.of(args[1]), args[2], Long.parseLong(args[3])
            );
        } else if ("probe".equals(mode)) {
            CrossJvmWorker.probe(
                Path.of(args[1]), args[2], Long.parseLong(args[3])
            );
        } else {
            throw new IllegalArgumentException(
                String.format("Unknown mode: %s", mode)
            );
        }
    }

    // Create pressure on the operating system's ephemeral allocator.
    private static void churn(final long millis, final Path heartbeat)
        throws IOException {
        final long deadline = System.currentTimeMillis() + millis;
        final List<ServerSocket> sockets = new ArrayList<>(
            CrossJvmWorker.CHURN_BATCH + 1
        );
        int allocations = 0;
        while (System.currentTimeMillis() < deadline) {
            try {
                sockets.add(new ServerSocket(0));
            } catch (final IOException failure) {
                CrossJvmWorker.LOG.log(
                    System.Logger.Level.DEBUG,
                    "Unable to allocate churn socket", failure
                );
            }
            if (sockets.size() > CrossJvmWorker.CHURN_BATCH) {
                allocations += sockets.size();
                if (!Files.isRegularFile(heartbeat)) {
                    CrossJvmWorker.publish(
                        heartbeat, Integer.toString(allocations)
                    );
                }
                CrossJvmWorker.close(sockets);
                sockets.clear();
            }
        }
        CrossJvmWorker.close(sockets);
    }

    // Measure the ordinary bind-zero race under allocator churn.
    private static void naive(final Path output, final int runs,
        final long hold) throws Exception {
        int lost = 0;
        for (int idx = 0; idx < runs; idx += 1) {
            final int port;
            try (ServerSocket probe = new ServerSocket(0)) {
                port = probe.getLocalPort();
            }
            Thread.sleep(hold);
            try (ServerSocket rebound = new ServerSocket(port)) {
                rebound.getLocalPort();
            } catch (final SocketException stolen) {
                lost += 1;
            }
        }
        CrossJvmWorker.publish(
            output, String.format("%d %d%n", lost, runs)
        );
    }

    // Publish a naively selected port and prove another JVM can steal it.
    private static void owner(final Path output, final Path offer,
        final Path ready) throws Exception {
        final int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        CrossJvmWorker.publish(offer, Integer.toString(port));
        CrossJvmWorker.await(ready);
        try (ServerSocket socket = new ServerSocket(port)) {
            throw new IllegalStateException(
                String.format("Naive port %d was not stolen", socket.getLocalPort())
            );
        } catch (final SocketException expected) {
            CrossJvmWorker.publish(
                output, "occupied"
            );
        }
    }

    // Bind the port published by the naïve owner.
    private static void thief(final Path offer, final Path ready,
        final long hold) throws Exception {
        CrossJvmWorker.await(offer);
        final int port = Integer.parseInt(Files.readString(offer));
        try (ServerSocket socket = CrossJvmWorker.bind(port)) {
            CrossJvmWorker.publish(
                ready, Integer.toString(socket.getLocalPort())
            );
            Thread.sleep(hold);
        }
    }

    // Bind a targeted port despite transient allocator-churn ownership.
    private static ServerSocket bind(final int port) throws Exception {
        final long deadline = System.currentTimeMillis()
            + CrossJvmWorker.COORDINATION_TIMEOUT / 2L;
        ServerSocket socket = null;
        while (socket == null && System.currentTimeMillis() < deadline) {
            try {
                socket = new ServerSocket(port);
            } catch (final SocketException occupied) {
                Thread.sleep(10L);
            }
        }
        if (socket == null) {
            throw new BindException(
                String.format("Offered port stayed occupied: %d", port)
            );
        }
        return socket;
    }

    // Repeatedly acquire through the real pool and bind a server.
    private static void pool(final String... args) throws Exception {
        final Path output = Path.of(args[1]);
        final int runs = Integer.parseInt(args[2]);
        final long hold = Long.parseLong(args[3]);
        final String range = args[4];
        final Path ready = Path.of(args[5]);
        final Path start = Path.of(args[6]);
        final StringBuilder ports = new StringBuilder(runs * 6);
        CrossJvmWorker.publish(ready, "ready");
        CrossJvmWorker.await(start);
        for (int idx = 0; idx < runs; idx += 1) {
            try (
                Reservation reservation = new Ports(
                    range, 20_000L
                ).acquire();
                ServerSocket server = new ServerSocket(reservation.port())
            ) {
                ports.append(reservation.port()).append(System.lineSeparator());
                Thread.sleep(hold);
            }
        }
        CrossJvmWorker.publish(output, ports.toString());
    }

    // Hold one reservation until killed or the timeout expires.
    private static void hold(final Path ready, final String range,
        final long millis) throws Exception {
        try (Reservation reservation = new Ports(range, 4000L).acquire()) {
            CrossJvmWorker.publish(
                ready, Integer.toString(reservation.port())
            );
            Thread.sleep(millis);
        }
    }

    // Report whether a range can be acquired.
    private static void probe(final Path directory, final String range,
        final long millis) throws Exception {
        CrossJvmWorker.publish(directory.resolve("ready.txt"), "ready");
        String result = "acquired";
        try (Reservation ignored = new Ports(range, millis).acquire()) {
            ignored.port();
        } catch (final NoFreePortException unavailable) {
            result = "blocked";
        }
        CrossJvmWorker.publish(directory.resolve("result.txt"), result);
    }

    // Wait for another worker's coordination file.
    private static void await(final Path file) throws Exception {
        final long deadline = System.currentTimeMillis()
            + CrossJvmWorker.COORDINATION_TIMEOUT;
        while (!Files.isRegularFile(file)
            && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                String.format("Coordination timed out: %s", file)
            );
        }
    }

    // Publish a coordination result atomically.
    private static void publish(final Path file, final String value)
        throws IOException {
        final Path temporary = file.resolveSibling(
            String.format(
                "%s.%d.tmp", file.getFileName(),
                ProcessHandle.current().pid()
            )
        );
        Files.writeString(temporary, value);
        try {
            Files.move(
                temporary, file, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (final AtomicMoveNotSupportedException unsupported) {
            Files.move(
                temporary, file, StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    // Close every socket in a churn batch.
    private static void close(final List<ServerSocket> sockets)
        throws IOException {
        for (final ServerSocket socket : sockets) {
            socket.close();
        }
    }
}
