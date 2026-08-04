/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link Candidates}.
 * @since 0.1.1
 */
final class CandidatesTest {

    @Test
    void drawsFromItsRange(@TempDir final Path folder) {
        try (
            Reservation held = new Candidates(
                new Range("20000-20000"), new Registry(folder)
            ).made().orElseThrow()
        ) {
            Assertions.assertEquals(20_000, held.port());
        }
    }

    @Test
    void keepsQuietWhenTheRegistryIsUnusable(@TempDir final Path folder)
        throws IOException {
        Assertions.assertEquals(
            Optional.empty(),
            new Candidates(
                new Range("20000-20000"),
                new Registry(Files.writeString(folder.resolve("occupied"), ""))
            ).made()
        );
    }
}
