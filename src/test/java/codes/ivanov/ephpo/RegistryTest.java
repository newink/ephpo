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
 * Tests for {@link Registry}.
 * @since 0.1.1
 */
final class RegistryTest {

    @Test
    void holdsOnePortRegion(@TempDir final Path folder) throws IOException {
        try (
            Reservation held = new Registry(folder).hold(20_000).orElseThrow()
        ) {
            Assertions.assertEquals(20_000, held.port());
        }
    }

    @Test
    void refusesRegionsThisJvmAlreadyHolds(@TempDir final Path folder)
        throws IOException {
        try (
            Reservation held = new Registry(folder).hold(20_001).orElseThrow()
        ) {
            Assertions.assertEquals(
                Optional.empty(), new Registry(folder).hold(held.port())
            );
        }
    }

    @Test
    void reopensARegionGivenBack(@TempDir final Path folder)
        throws IOException {
        new Registry(folder).hold(20_002).orElseThrow().close();
        try (
            Reservation again = new Registry(folder).hold(20_002).orElseThrow()
        ) {
            Assertions.assertEquals(20_002, again.port());
        }
    }

    @Test
    void reportsAnUnopenableRegistryFile(@TempDir final Path folder)
        throws IOException {
        Files.createDirectory(folder.resolve("registry.lock"));
        final Registry registry = new Registry(folder);
        Assertions.assertEquals(
            "Unable to open the port lock registry",
            Assertions.assertThrows(
                IOException.class, () -> registry.hold(20_003)
            ).getMessage()
        );
    }

    @Test
    void reportsAnUncreatableRegistryDirectory(@TempDir final Path folder)
        throws IOException {
        final Registry registry = new Registry(
            Files.writeString(folder.resolve("occupied"), "")
        );
        Assertions.assertThrows(
            IOException.class, () -> registry.hold(20_004)
        );
    }
}
