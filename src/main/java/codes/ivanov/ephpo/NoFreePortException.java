/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

/**
 * Thrown when a pool cannot reserve a port before its timeout.
 * @since 0.1.0
 */
public final class NoFreePortException extends IllegalStateException {

    /** Serialization marker. */
    private static final long serialVersionUID = 1L;

    /**
     * New exhausted-pool exception.
     * @param message Failure diagnostics
     */
    NoFreePortException(final String message) {
        super(message);
    }
}
