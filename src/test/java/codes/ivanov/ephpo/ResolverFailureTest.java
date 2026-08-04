/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Tests diagnostics by launching deliberately invalid test classes.
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.AvoidDirectAccessToStaticFields",
    "PMD.UnitTestContainsTooManyAsserts"
})
final class ResolverFailureTest {

    @Test
    void rejectsWrongParameterType() {
        ResolverFailureTest.assertFailure(
            WrongParameter.class, "must be int or Integer"
        );
    }

    @Test
    void rejectsStaticFields() {
        ResolverFailureTest.assertFailure(
            StaticField.class, "instance fields only"
        );
    }

    @Test
    void reportsExhaustedRanges() {
        ResolverFailureTest.assertFailure(
            Exhausted.class, "-Dephpo.range=MIN-MAX"
        );
    }

    @Test
    void rejectsConcurrentPerClassFields() {
        ResolverFailureTest.assertFailure(
            ConcurrentPerClassField.class,
            "cannot be injected into a concurrent PER_CLASS test instance"
        );
    }

    @Test
    void supportsConcurrentPerClassParameters() {
        ResolverFailureTest.assertSuccess(ConcurrentPerClassParameter.class, 8L);
    }

    @Test
    void supportsConcurrentPerMethodFields() {
        ResolverFailureTest.assertSuccess(ConcurrentPerMethodField.class, 8L);
    }

    @Test
    void serviceLoaderEnablesBareAnnotation() {
        final TestExecutionSummary summary = ResolverFailureTest.execute(
            BareAnnotation.class, true
        );
        Assertions.assertEquals(0L, summary.getTestsFailedCount());
        Assertions.assertEquals(1L, summary.getTestsSucceededCount());
    }

    /**
     * Assert a launched test fails with useful text.
     * @param type Test class
     * @param text Expected message fragment
     */
    private static void assertFailure(final Class<?> type, final String text) {
        final List<TestExecutionSummary.Failure> failures =
            ResolverFailureTest.execute(type, false).getFailures();
        Assertions.assertEquals(1, failures.size());
        Assertions.assertTrue(
            failures.get(0).getException().toString().contains(text),
            failures.get(0).getException()::toString
        );
    }

    /**
     * Assert all launched tests succeed.
     * @param type Test class
     * @param total Expected successful tests
     */
    private static void assertSuccess(final Class<?> type, final long total) {
        final TestExecutionSummary summary = ResolverFailureTest.execute(
            type, false
        );
        Assertions.assertEquals(0L, summary.getTestsFailedCount());
        Assertions.assertEquals(total, summary.getTestsSucceededCount());
    }

    /**
     * Launch one nested test class.
     * @param type Test class
     * @param autodetect Whether extension auto-detection is enabled
     * @return Execution summary
     * @since 0.1.0
     */
    private static TestExecutionSummary execute(final Class<?> type,
        final boolean autodetect) {
        final LauncherDiscoveryRequestBuilder builder =
            LauncherDiscoveryRequestBuilder.request();
        final LauncherDiscoveryRequest request = builder.selectors(
            DiscoverySelectors.selectClass(type)
        ).configurationParameter(
            "junit.jupiter.extensions.autodetection.enabled",
            Boolean.toString(autodetect)
        ).configurationParameter(
            "junit.jupiter.execution.parallel.enabled", "true"
        ).build();
        final SummaryGeneratingListener listener =
            new SummaryGeneratingListener();
        final Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(
            new TestExecutionListener[] {listener}
        );
        launcher.execute(request);
        return listener.getSummary();
    }

    /** Wrong parameter type case. */
    @ExtendWith(EphemeralResolver.class)
    static final class WrongParameter {

        @Test
        void invalid(@Ephemeral final String port) {
            Assertions.fail(String.format("unexpected parameter: %s", port));
        }
    }

    /** Static field case. */
    @ExtendWith(EphemeralResolver.class)
    static final class StaticField {

        /** Invalid static target. */
        @Ephemeral
        private static int port;

        @Test
        void invalid() {
            Assertions.fail(
                String.format("unexpected field: %d", StaticField.port)
            );
        }
    }

    /** Exhausted range case. */
    @ExtendWith(TinyResolver.class)
    static final class Exhausted {

        @Test
        void invalid(@Ephemeral final int first, @Ephemeral final int second) {
            Assertions.fail(
                String.format("unexpected ports: %d, %d", first, second)
            );
        }
    }

    /** Auto-detected extension case. */
    static final class BareAnnotation {

        @Test
        void valid(@Ephemeral final int port) {
            Assertions.assertTrue(port > 0);
        }
    }

    /** Concurrent shared-instance field case. */
    @ExtendWith(EphemeralResolver.class)
    @Execution(ExecutionMode.CONCURRENT)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static final class ConcurrentPerClassField {

        /** Unsafe shared target. */
        @Ephemeral
        private int port;

        @Test
        void invalid() {
            Assertions.assertTrue(this.port > 0);
        }
    }

    /** Concurrent shared-instance parameter case. */
    @ExtendWith(EphemeralResolver.class)
    @Execution(ExecutionMode.CONCURRENT)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static final class ConcurrentPerClassParameter {

        @RepeatedTest(8)
        void valid(@Ephemeral final int port) {
            Assertions.assertTrue(port > 0);
        }
    }

    /** Concurrent per-invocation field case. */
    @ExtendWith(EphemeralResolver.class)
    @Execution(ExecutionMode.CONCURRENT)
    static final class ConcurrentPerMethodField {

        /** Safe per-invocation target. */
        @Ephemeral
        private int port;

        @RepeatedTest(8)
        void valid() {
            Assertions.assertTrue(this.port > 0);
        }
    }

    /** Resolver backed by a one-port pool. */
    static final class TinyResolver implements ParameterResolver {

        /** Actual resolver. */
        private final EphemeralResolver origin = new EphemeralResolver(
            new Ports("29997-29997", 25L)
        );

        @Override
        public boolean supportsParameter(final ParameterContext context,
            final ExtensionContext extension) {
            return this.origin.supportsParameter(context, extension);
        }

        @Override
        public Object resolveParameter(final ParameterContext context,
            final ExtensionContext extension) {
            return this.origin.resolveParameter(context, extension);
        }
    }
}
