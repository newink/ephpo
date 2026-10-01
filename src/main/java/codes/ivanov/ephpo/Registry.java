/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The host-wide registry of port locks, one byte-range region per port.
 *
 * <p>POSIX drops every lock a process holds on a file the moment that
 * process closes any descriptor for it. One channel per registry file is
 * therefore shared by every reservation in this JVM and closed only once
 * the last of them has been given back.</p>
 *
 * <p>The ports this JVM holds are not tracked separately: the JVM refuses
 * overlapping locks on a file whichever channel or thread asks for them,
 * so {@link OverlappingFileLockException} already reports them.</p>
 *
 * @since 0.1.1
 */
@SuppressWarnings("PMD.CloseResource")
final class Registry {

    /** Diagnostic logger. */
    private static final System.Logger LOG = System.getLogger(
        Registry.class.getName()
    );

    /** Channels shared by every reservation on their registry file. */
    private static final Map<Path, Shared> SHARED =
        new ConcurrentHashMap<>(1);

    /** Registry directory. */
    private final Path directory;

    /**
     * New registry.
     * @param folder Registry directory
     */
    Registry(final Path folder) {
        this.directory = folder;
    }

    /**
     * Lock one port's region.
     * @param port Candidate port
     * @return Held reservation, when the region was free
     * @throws IOException When the registry cannot be opened
     */
    Optional<Reservation> hold(final int port) throws IOException {
        Files.createDirectories(this.directory);
        final Shared shared = Registry.shared(
            this.directory.resolve("registry.lock")
        );
        Optional<Reservation> result = Optional.empty();
        try {
            final FileLock lock = shared.channel().tryLock(port, 1L, false);
            if (lock != null) {
                result = Optional.of(new Held(port, lock, shared::give));
            }
        } catch (final OverlappingFileLockException taken) {
            Registry.LOG.log(
                System.Logger.Level.TRACE,
                "Candidate port is already reserved by this JVM", taken
            );
        } finally {
            if (result.isEmpty()) {
                shared.give();
            }
        }
        return result;
    }

    // Take one reference to the JVM's channel for a registry file.
    private static Shared shared(final Path file) throws IOException {
        final Shared result;
        try {
            result = Registry.SHARED.compute(file, Registry::taken);
        } catch (final UncheckedIOException wrapped) {
            throw new IOException(
                "Unable to open the port lock registry", wrapped
            );
        }
        return result;
    }

    // Count one more reference in, opening the channel when it is the first.
    private static Shared taken(final Path file, final Shared existing) {
        Shared result = existing;
        if (result == null) {
            result = new Shared(file, Registry.open(file), Registry.SHARED);
        }
        result.take();
        return result;
    }

    // Open one registry file.
    private static FileChannel open(final Path file) {
        try {
            return FileChannel.open(
                file, StandardOpenOption.CREATE, StandardOpenOption.WRITE
            );
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
