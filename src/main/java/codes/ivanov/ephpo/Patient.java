/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.util.Optional;

/**
 * A pool that keeps retrying an attempt until its deadline.
 * @since 0.1.1
 */
final class Patient implements Pool {

    /** Wrapped attempt. */
    private final Attempt origin;

    /** Range the attempt draws from, for diagnostics. */
    private final Range range;

    /** Acquisition timeout. */
    private final long timeout;

    /**
     * New patient pool.
     * @param attempt Wrapped attempt
     * @param span Range the attempt draws from
     * @param millis Acquisition timeout in milliseconds
     */
    Patient(final Attempt attempt, final Range span, final long millis) {
        if (millis < 1L) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.origin = attempt;
        this.range = span;
        this.timeout = millis;
    }

    @Override
    public Reservation acquire() {
        final long deadline = System.currentTimeMillis() + this.timeout;
        Optional<Reservation> found;
        int attempts = 0;
        do {
            attempts += 1;
            found = this.origin.made();
            if (found.isPresent() || Patient.exhausted(attempts)) {
                break;
            }
        } while (System.currentTimeMillis() < deadline);
        final int total = attempts;
        return found.orElseThrow(
            () -> new NoFreePortException(this.failure(total))
        );
    }

    // Yield the CPU every so often, and report interruption.
    private static boolean exhausted(final int attempts) {
        boolean stop = false;
        if (attempts % 64 == 0) {
            try {
                Thread.sleep(1L);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                stop = true;
            }
        }
        return stop;
    }

    // Build actionable pool-exhaustion diagnostics.
    private String failure(final int attempts) {
        return String.format(
            String.join(
                "", "Couldn't find a free TCP port in %s after %d attempts ",
                "over %dms. Either the range is too small for the number of ",
                "concurrent tests, or something is squatting on it -- widen ",
                "it with -Dephpo.range=MIN-MAX."
            ),
            this.range, attempts, this.timeout
        );
    }
}
