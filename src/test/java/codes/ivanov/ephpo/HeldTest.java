/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link Held}.
 * @since 0.1.1
 */
@SuppressWarnings({
    "PMD.CloseResource",
    "PMD.UnitTestContainsTooManyAsserts"
})
final class HeldTest {

    @Test
    void releasesItsLockOnce(@TempDir final Path folder) throws IOException {
        final AtomicInteger given = new AtomicInteger();
        try (FileChannel channel = HeldTest.channel(folder)) {
            final FileLock lock = channel.lock(20_005, 1L, false);
            final Reservation held = new Held(
                20_005, lock, given::incrementAndGet
            );
            held.close();
            held.close();
            Assertions.assertFalse(lock.isValid());
            Assertions.assertEquals(1, given.get());
        }
    }

    @Test
    void givesItsChannelBackWhenTheLockCannotBeReleased(
        @TempDir final Path folder
    ) throws IOException {
        final FileLock lock;
        try (FileChannel channel = HeldTest.channel(folder)) {
            lock = channel.lock(20_006, 1L, false);
        }
        final AtomicInteger given = new AtomicInteger();
        new Held(20_006, lock, given::incrementAndGet).close();
        Assertions.assertEquals(1, given.get());
    }

    // Open a registry-like file.
    private static FileChannel channel(final Path folder) throws IOException {
        return FileChannel.open(
            folder.resolve("registry.lock"),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE
        );
    }
}
