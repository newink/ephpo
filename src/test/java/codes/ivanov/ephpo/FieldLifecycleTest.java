/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves field reservations are released after every invocation.
 * @since 0.1.0
 */
@ExtendWith(EphemeralResolver.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class FieldLifecycleTest {

    /** Per-invocation port. */
    @Ephemeral
    private int port;

    @RepeatedTest(40)
    void holdsExactlyOnePort() {
        Assertions.assertTrue(this.port > 0);
        Assertions.assertEquals(1, Ports.heldCount());
    }
}
