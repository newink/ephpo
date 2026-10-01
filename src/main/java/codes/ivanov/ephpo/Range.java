/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * An inclusive range of TCP port numbers.
 * @since 0.1.1
 */
@SuppressWarnings("PMD.ConstructorOnlyInitializesOrCallOtherConstructors")
final class Range {

    /** Lower boundary. */
    private final int lower;

    /** Upper boundary. */
    private final int upper;

    /**
     * New range from its textual form.
     * @param text Inclusive {@code MIN-MAX}
     */
    // @checkstyle ConstructorsCodeFreeCheck (18 lines)
    Range(final String text) {
        final String[] parts = text.split("-", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                String.format("Invalid port range: %s", text)
            );
        }
        this.lower = Integer.parseInt(parts[0].trim());
        this.upper = Integer.parseInt(parts[1].trim());
        if (this.lower < 1 || this.upper > 65_535
            || this.lower > this.upper) {
            throw new IllegalArgumentException(
                String.format("Invalid port range: %s", text)
            );
        }
    }

    @Override
    public String toString() {
        return String.format("%d-%d", this.lower, this.upper);
    }

    /**
     * Pick one port at random.
     * @return Port number
     */
    int random() {
        return ThreadLocalRandom.current().nextInt(this.lower, this.upper + 1);
    }
}
