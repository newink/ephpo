/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Attempts kept only when nothing is already listening on the port.
 * @since 0.1.1
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
final class Bindable implements Attempt {

    /** Diagnostic logger. */
    private static final System.Logger LOG = System.getLogger(
        Bindable.class.getName()
    );

    /** Local interfaces requiring probes, particularly on macOS. */
    private static final String[] HOSTS = {
        "0.0.0.0", "127.0.0.1", "::1", "localhost",
    };

    /** Wrapped attempt. */
    private final Attempt origin;

    /**
     * New probing attempt.
     * @param attempt Wrapped attempt
     */
    Bindable(final Attempt attempt) {
        this.origin = attempt;
    }

    @Override
    public Optional<Reservation> made() {
        Optional<Reservation> result = this.origin.made();
        if (result.isPresent() && !Bindable.free(result.get().port())) {
            result.get().close();
            result = Optional.empty();
        }
        return result;
    }

    /**
     * Probe a port on every relevant local interface.
     * @param port Candidate port
     * @return Whether every probe succeeded
     */
    private static boolean free(final int port) {
        boolean result = true;
        for (final String host : Bindable.HOSTS) {
            final Optional<InetAddress> address = Bindable.resolve(host);
            if (address.isPresent()) {
                try (ServerSocket socket = new ServerSocket()) {
                    socket.bind(
                        new InetSocketAddress(address.get(), port), 1
                    );
                } catch (final IOException occupied) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Resolve a local probe interface.
     * @param host Interface name or address
     * @return The address, when this host knows it
     */
    private static Optional<InetAddress> resolve(final String host) {
        Optional<InetAddress> result;
        try {
            result = Optional.of(InetAddress.getByName(host));
        } catch (final UnknownHostException unavailable) {
            Bindable.LOG.log(
                System.Logger.Level.DEBUG,
                "Unable to resolve local probe interface", unavailable
            );
            result = Optional.empty();
        }
        return result;
    }
}
