/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One port and the host-wide lock that holds it.
 * @since 0.1.1
 */
final class Held implements Reservation {

    /** Diagnostic logger. */
    private static final System.Logger LOG = System.getLogger(
        Held.class.getName()
    );

    /** Port number. */
    private final int value;

    /** Host-wide lock. */
    private final FileLock lock;

    /** What to run once the lock is gone. */
    private final Runnable released;

    /** Whether this reservation still owns the lock. */
    private final AtomicBoolean live;

    /**
     * New held port.
     * @param port Port number
     * @param guard Host-wide lock
     * @param callback What to run once the lock is gone
     */
    Held(final int port, final FileLock guard, final Runnable callback) {
        this.value = port;
        this.lock = guard;
        this.released = callback;
        this.live = new AtomicBoolean(true);
    }

    @Override
    public int port() {
        return this.value;
    }

    @Override
    public void close() {
        if (this.live.compareAndSet(true, false)) {
            try {
                this.lock.release();
            } catch (final IOException failure) {
                Held.LOG.log(
                    System.Logger.Level.DEBUG,
                    "Unable to release port lock", failure
                );
            }
            this.released.run();
        }
    }
}
