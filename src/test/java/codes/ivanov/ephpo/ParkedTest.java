/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Tests for {@link Parked}.
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
final class ParkedTest {

    @Test
    void releasesWhatItParks() {
        final AtomicInteger closed = new AtomicInteger();
        new Parked(ParkedTest.reservation(closed)).close();
        Assertions.assertEquals(1, closed.get());
    }

    @Test
    void closesThroughTheModernJunitContract() {
        Assertions.assertTrue(
            AutoCloseable.class.isAssignableFrom(Parked.class)
        );
    }

    @Test
    void closesThroughTheLegacyJunitContract() {
        Assertions.assertTrue(
            ExtensionContext.Store.CloseableResource.class
                .isAssignableFrom(Parked.class)
        );
    }

    // Reservation counting its own closures.
    private static Reservation reservation(final AtomicInteger closed) {
        return new Reservation() {
            @Override
            public int port() {
                return 20_000;
            }

            @Override
            public void close() {
                closed.incrementAndGet();
            }
        };
    }
}
