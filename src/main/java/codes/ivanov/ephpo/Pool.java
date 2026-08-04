/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import org.apiguardian.api.API;

/**
 * A source of TCP ports reserved across cooperating processes on this host.
 *
 * <p>Ports come from {@code 20000-29999} by default, outside the automatic
 * allocation ranges of supported operating systems. Each port is protected
 * by a host-wide file lock and verified bindable before it is returned.</p>
 *
 * @since 0.1.0
 */
@API(status = API.Status.STABLE, since = "0.1.0")
@FunctionalInterface
public interface Pool {

    /**
     * The recommended shared pool.
     */
    Pool SINGLETON = new Ports();

    /**
     * Reserve one port.
     * @return An open reservation
     * @throws NoFreePortException If no port becomes available in time
     */
    Reservation acquire() throws NoFreePortException;
}
