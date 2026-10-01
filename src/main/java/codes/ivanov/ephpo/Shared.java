/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Map;

/**
 * One open channel and the reservations still relying on it.
 * @since 1.0.1
 */
final class Shared {

    /** Diagnostic logger. */
    private static final System.Logger LOG = System.getLogger(
        Shared.class.getName()
    );

    /** Registry file. */
    private final Path file;

    /** Channel shared by every reservation on the file. */
    private final FileChannel origin;

    /** Channels indexed by registry file. */
    private final Map<Path, Shared> registry;

    /** Reservations relying on the channel. */
    private int count;

    /**
     * New shared channel.
     * @param path Registry file
     * @param channel Channel
     * @param channels Shared registry map
     */
    Shared(final Path path, final FileChannel channel,
        final Map<Path, Shared> channels) {
        this.file = path;
        this.origin = channel;
        this.registry = channels;
        this.count = 0;
    }

    /**
     * Channel on which port regions are locked.
     * @return Open channel
     */
    FileChannel channel() {
        return this.origin;
    }

    /**
     * Count one reference while the registry entry is locked by compute.
     */
    void take() {
        this.count += 1;
    }

    /**
     * Give one reference back, closing the channel with the last one.
     *
     * <p>A null compute result removes the closed channel from the map.</p>
     */
    @SuppressWarnings("PMD.NullAssignment")
    void give() {
        this.registry.computeIfPresent(
            this.file,
            (path, current) -> {
                Shared result = current;
                current.count -= 1;
                if (current.count == 0) {
                    current.close();
                    result = null;
                }
                return result;
            }
        );
    }

    // Close the last reference without masking an earlier failure.
    private void close() {
        try {
            this.origin.close();
        } catch (final IOException failure) {
            Shared.LOG.log(
                System.Logger.Level.DEBUG,
                "Unable to close port lock channel", failure
            );
        }
    }
}
