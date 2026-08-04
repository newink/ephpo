/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.util.Optional;

/**
 * Random ports from a range, claimed in a host-wide registry.
 * @since 0.1.1
 */
final class Candidates implements Attempt {

    /** Diagnostic logger. */
    private static final System.Logger LOG = System.getLogger(
        Candidates.class.getName()
    );

    /** Range to draw from. */
    private final Range range;

    /** Registry to claim in. */
    private final Registry registry;

    /**
     * New source of candidates.
     * @param span Range to draw from
     * @param origin Registry to claim in
     */
    Candidates(final Range span, final Registry origin) {
        this.range = span;
        this.registry = origin;
    }

    @Override
    public Optional<Reservation> made() {
        Optional<Reservation> result = Optional.empty();
        try {
            result = this.registry.hold(this.range.random());
        } catch (final IOException failure) {
            Candidates.LOG.log(
                System.Logger.Level.DEBUG,
                "Unable to lock candidate port", failure
            );
        }
        return result;
    }
}
