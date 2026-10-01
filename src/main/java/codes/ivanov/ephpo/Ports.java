/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.nio.file.Path;
import org.apiguardian.api.API;

/**
 * Random non-ephemeral ports guarded by host-wide byte-range locks.
 * @since 0.1.0
 */
@API(status = API.Status.STABLE, since = "0.1.0")
public final class Ports implements Pool {

    /** Composed pool. */
    private final Pool origin;

    /**
     * New pool from system properties.
     */
    public Ports() {
        this(
            System.getProperty("ephpo.range", "20000-29999"),
            Long.getLong("ephpo.timeout", 4000L)
        );
    }

    /**
     * New pool with an explicit range and timeout.
     * @param range Inclusive {@code MIN-MAX}
     * @param millis Acquisition timeout in milliseconds
     */
    public Ports(final String range, final long millis) {
        this(new Range(range), millis);
    }

    /**
     * New pool over a parsed range.
     * @param range Port range
     * @param millis Acquisition timeout in milliseconds
     */
    private Ports(final Range range, final long millis) {
        this(
            new Patient(
                new Bindable(
                    new Candidates(
                        range,
                        new Registry(
                            Path.of(
                                System.getProperty("java.io.tmpdir"),
                                "ephpo"
                            ).toAbsolutePath().normalize()
                        )
                    )
                ),
                range,
                millis
            )
        );
    }

    /**
     * New pool delegating to a composition.
     * @param composed Composed pool
     */
    private Ports(final Pool composed) {
        this.origin = composed;
    }

    @Override
    public Reservation acquire() {
        return this.origin.acquire();
    }
}
