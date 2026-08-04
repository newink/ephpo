/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Proves the build skeleton runs tests at all.
 * @since 0.1.0
 */
final class SkeletonTest {

    @Test
    void runsUnderSurefire() {
        Assertions.assertTrue(
            Test.class.isAnnotation(),
            "JUnit's Test type must be an annotation"
        );
    }
}
