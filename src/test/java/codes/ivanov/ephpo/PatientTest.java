/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Patient}.
 * @since 0.1.1
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class PatientTest {

    /** Range the fake attempts pretend to draw from. */
    private static final String RANGE = "20000-20001";

    @Test
    void rejectsNonPositiveTimeouts() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new Patient(
                Optional::empty, new Range(PatientTest.RANGE), 0L
            )
        );
    }

    @Test
    void retriesUntilAnAttemptSucceeds() {
        final AtomicInteger attempts = new AtomicInteger();
        Assertions.assertEquals(
            20_000,
            new Patient(
                () -> {
                    final Optional<Reservation> made;
                    if (attempts.incrementAndGet() < 3) {
                        made = Optional.empty();
                    } else {
                        made = Optional.of(new FakeReservation(20_000));
                    }
                    return made;
                },
                new Range(PatientTest.RANGE), 5000L
            ).acquire().port()
        );
        Assertions.assertEquals(3, attempts.get());
    }

    @Test
    void reportsTheRangeAndTheAttemptCount() {
        final NoFreePortException failure = Assertions.assertThrows(
            NoFreePortException.class,
            () -> new Patient(
                Optional::empty, new Range(PatientTest.RANGE), 1L
            ).acquire()
        );
        Assertions.assertTrue(
            failure.getMessage().contains(PatientTest.RANGE),
            failure::getMessage
        );
        Assertions.assertTrue(
            failure.getMessage().contains("over 1ms"), failure::getMessage
        );
    }

    @Test
    void stopsRetryingWhenTheThreadIsInterrupted() {
        final AtomicInteger attempts = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            Assertions.assertThrows(
                NoFreePortException.class,
                () -> new Patient(
                    () -> {
                        attempts.incrementAndGet();
                        return Optional.empty();
                    },
                    new Range(PatientTest.RANGE), 10_000L
                ).acquire()
            );
            Assertions.assertEquals(64, attempts.get());
            Assertions.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
