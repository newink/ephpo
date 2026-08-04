/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Random non-ephemeral ports guarded by host-wide file locks.
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.AvoidSynchronizedStatement",
    "PMD.AvoidUsingHardCodedIP",
    "PMD.CloseResource",
    "PMD.ConstructorOnlyInitializesOrCallOtherConstructors",
    "PMD.NullAssignment",
    "PMD.UseTryWithResources"
})
public final class Ports implements Pool {

    /** Diagnostic logger. */
    private static final System.Logger LOG = System.getLogger(
        Ports.class.getName()
    );

    /** Default allocation range. */
    private static final String DEFAULT_RANGE = "20000-29999";

    /** Default acquisition timeout. */
    private static final long DEFAULT_TIMEOUT = 4000L;

    /** Retry count between cooperative sleeps. */
    private static final int BACKOFF_FREQUENCY = 64;

    /** Cooperative sleep duration. */
    private static final long BACKOFF_MILLIS = 1L;

    /** Local interfaces requiring probes, particularly on macOS. */
    private static final String[] HOSTS = {
        "0.0.0.0", "127.0.0.1", "::1", "localhost",
    };

    /** Ports held by this JVM across every pool instance. */
    private static final Set<Integer> HELD = new HashSet<>(0);

    /** Lower range boundary. */
    private final int min;

    /** Upper range boundary. */
    private final int max;

    /** Lock registry directory. */
    private final Path directory;

    /** Acquisition timeout. */
    private final long timeout;

    /**
     * New pool from system properties.
     */
    // @checkstyle ConstructorsCodeFreeCheck (32 lines)
    public Ports() {
        this(
            System.getProperty("ephpo.range", Ports.DEFAULT_RANGE),
            Long.getLong("ephpo.timeout", Ports.DEFAULT_TIMEOUT)
        );
    }

    /**
     * New pool with an explicit range and timeout.
     * @param range Inclusive {@code MIN-MAX}
     * @param millis Acquisition timeout in milliseconds
     */
    public Ports(final String range, final long millis) {
        final int[] bounds = Ports.bounds(range);
        if (millis < 1L) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.min = bounds[0];
        this.max = bounds[1];
        this.directory = Path.of(
            System.getProperty("java.io.tmpdir"), "ephpo"
        );
        this.timeout = millis;
    }

    @Override
    public Reservation acquire() {
        final long deadline = System.currentTimeMillis() + this.timeout;
        int attempts = 0;
        do {
            attempts += 1;
            final int port = ThreadLocalRandom.current().nextInt(
                this.min, this.max + 1
            );
            final Optional<Held> candidate = this.hold(port);
            if (candidate.isPresent()) {
                final Held held = candidate.get();
                if (Ports.bindable(port)) {
                    return held;
                }
                held.close();
            }
            if (attempts % Ports.BACKOFF_FREQUENCY == 0) {
                try {
                    Thread.sleep(Ports.BACKOFF_MILLIS);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (System.currentTimeMillis() < deadline);
        throw new NoFreePortException(this.failure(attempts));
    }

    /**
     * Count ports held by this JVM.
     * @return Current count
     */
    static int heldCount() {
        synchronized (Ports.HELD) {
            return Ports.HELD.size();
        }
    }

    /**
     * Parse and validate a range.
     * @param range Inclusive {@code MIN-MAX}
     * @return Two boundaries
     */
    private static int[] bounds(final String range) {
        final String[] parts = range.split("-", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                String.format("Invalid port range: %s", range)
            );
        }
        final int lower = Integer.parseInt(parts[0].trim());
        final int upper = Integer.parseInt(parts[1].trim());
        if (lower < 1 || upper > 65_535 || lower > upper) {
            throw new IllegalArgumentException(
                String.format("Invalid port range: %s", range)
            );
        }
        return new int[] {lower, upper};
    }

    /**
     * Try to lock one port.
     * @param port Candidate port
     * @return Held reservation, if acquired
     */
    private Optional<Held> hold(final int port) {
        Optional<Ports.Held> result = Optional.empty();
        synchronized (Ports.HELD) {
            if (Ports.HELD.add(port)) {
                result = this.lock(port);
            }
        }
        return result;
    }

    /**
     * Open and lock one registry entry.
     * @param port Candidate port
     * @return Held reservation, if acquired
     */
    private Optional<Ports.Held> lock(final int port) {
        Optional<Ports.Held> result = Optional.empty();
        FileChannel channel = null;
        try {
            Files.createDirectories(this.directory);
            channel = FileChannel.open(
                this.directory.resolve(String.format("%d.lock", port)),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            );
            final FileLock lock = channel.tryLock();
            if (lock != null) {
                result = Optional.of(new Ports.Held(port, channel, lock));
                channel = null;
            }
        } catch (final IOException failure) {
            Ports.LOG.log(
                System.Logger.Level.DEBUG,
                "Unable to lock candidate port", failure
            );
        } finally {
            if (channel != null) {
                Ports.close(channel);
            }
            if (result.isEmpty()) {
                synchronized (Ports.HELD) {
                    Ports.HELD.remove(port);
                }
            }
        }
        return result;
    }

    /**
     * Probe a port on every relevant local interface.
     * @param port Candidate port
     * @return Whether every probe succeeded
     */
    private static boolean bindable(final int port) {
        boolean result = true;
        for (final String host : Ports.HOSTS) {
            InetAddress address = null;
            try {
                address = InetAddress.getByName(host);
            } catch (final UnknownHostException unavailable) {
                Ports.LOG.log(
                    System.Logger.Level.DEBUG,
                    "Unable to resolve local probe interface", unavailable
                );
            }
            if (address != null) {
                try (ServerSocket socket = new ServerSocket()) {
                    socket.bind(new InetSocketAddress(address, port), 1);
                } catch (final IOException occupied) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Close a channel without masking reservation cleanup.
     * @param channel Channel
     */
    private static void close(final FileChannel channel) {
        try {
            channel.close();
        } catch (final IOException failure) {
            Ports.LOG.log(
                System.Logger.Level.DEBUG,
                "Unable to close port lock channel", failure
            );
        }
    }

    /**
     * Build actionable pool-exhaustion diagnostics.
     * @param attempts Candidate count
     * @return Failure message
     */
    private String failure(final int attempts) {
        return String.format(
            String.join(
                "", "Couldn't find a free TCP port in %d-%d after %d ",
                "attempts over %dms (%d already held by this JVM). Either ",
                "the range is too small for the number of concurrent tests, ",
                "or something is squatting on it -- widen it with ",
                "-Dephpo.range=MIN-MAX."
            ),
            this.min, this.max, attempts, this.timeout, Ports.heldCount()
        );
    }

    /**
     * One port and its lock.
     * @since 0.1.0
     */
    private static final class Held implements Reservation {

        /** Port number. */
        private final int value;

        /** Lock-file channel. */
        private final FileChannel channel;

        /** Host-wide lock. */
        private final FileLock lock;

        /** Whether this reservation still owns the lock. */
        private final AtomicBoolean open;

        /**
         * New held port.
         * @param port Port number
         * @param origin Lock-file channel
         * @param guard Host-wide lock
         */
        Held(final int port, final FileChannel origin, final FileLock guard) {
            this.value = port;
            this.channel = origin;
            this.lock = guard;
            this.open = new AtomicBoolean(true);
        }

        @Override
        public int port() {
            return this.value;
        }

        @Override
        public void close() {
            if (this.open.compareAndSet(true, false)) {
                try {
                    this.lock.release();
                } catch (final IOException failure) {
                    Ports.LOG.log(
                        System.Logger.Level.DEBUG,
                        "Unable to release port lock", failure
                    );
                }
                Ports.close(this.channel);
                synchronized (Ports.HELD) {
                    Ports.HELD.remove(this.value);
                }
            }
        }
    }
}
