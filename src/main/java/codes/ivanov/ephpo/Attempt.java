/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.util.Optional;

/**
 * One try at reserving a port.
 * @since 0.1.1
 */
@FunctionalInterface
interface Attempt {

    /**
     * Try once, without waiting.
     * @return Reservation, when the candidate port turned out to be free
     */
    Optional<Reservation> made();
}
