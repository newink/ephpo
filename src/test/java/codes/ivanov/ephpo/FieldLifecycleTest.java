/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Proves field reservations are released after every invocation.
 *
 * <p>The pool behind this class is only four ports wide, so a reservation
 * that outlives its invocation exhausts the range within a handful of
 * repetitions.</p>
 *
 * @since 0.1.0
 */
@ExtendWith(FieldLifecycleTest.Narrow.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class FieldLifecycleTest {

    /** Per-invocation port. */
    @Ephemeral
    private int port;

    @RepeatedTest(40)
    void holdsExactlyOnePort() {
        Assertions.assertTrue(this.port > 0);
    }

    /** Field injection backed by a four-port pool. */
    static final class Narrow implements BeforeEachCallback {

        /** Actual resolver. */
        private final EphemeralResolver origin = new EphemeralResolver(
            new Ports("29992-29995", 500L)
        );

        @Override
        public void beforeEach(final ExtensionContext extension) {
            this.origin.beforeEach(extension);
        }
    }
}
