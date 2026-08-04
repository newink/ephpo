/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests parameter and field injection.
 * @since 0.1.0
 */
@ExtendWith(EphemeralResolver.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class EphemeralResolverTest {

    /** Injected before each test. */
    @Ephemeral
    private int field;

    @BeforeEach
    void injectsBeforeLifecycleMethods() {
        Assertions.assertTrue(this.field > 0);
    }

    @Test
    void bindsInjectedField() throws IOException {
        try (ServerSocket server = new ServerSocket(this.field)) {
            Assertions.assertEquals(this.field, server.getLocalPort());
        }
    }

    @Test
    void injectsDistinctParameters(@Ephemeral final int first,
        @Ephemeral final Integer second) {
        Assertions.assertNotEquals(first, second.intValue());
        Assertions.assertNotEquals(this.field, first);
        Assertions.assertNotEquals(this.field, second.intValue());
    }

    @Test
    void coexistsWithOtherResolvers(final TestInfo info,
        @Ephemeral final int port) {
        Assertions.assertEquals(
            "coexistsWithOtherResolvers",
            info.getTestMethod().orElseThrow().getName()
        );
        Assertions.assertTrue(port > 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"first", "second"})
    void coexistsWithParameterizedTests(final String value,
        @Ephemeral final int port) {
        Assertions.assertFalse(value.isEmpty());
        Assertions.assertTrue(port >= 20_000 && port <= 29_999);
    }
}
