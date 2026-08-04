/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Sergei Ivanov
 * SPDX-License-Identifier: MIT
 */
package codes.ivanov.ephpo;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies the reservation guarantee with real, competing JVMs.
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.SystemPrintln",
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UseArraysAsList"
})
final class CrossJvmIT {

    /** Number of allocator churn processes. */
    private static final int CHURNERS = 4;

    /** Churn lifetime. */
    private static final long CHURN_MILLIS = 120_000L;

    /** Number of empirical bind-zero controls. */
    private static final int CONTROLS = 4;

    /** Attempts per empirical bind-zero control. */
    private static final int CONTROL_RUNS = 300;

    /** Bind-zero race window. */
    private static final long CONTROL_HOLD = 50L;

    /** Number of pool clients. */
    private static final int CLIENTS = 6;

    /** Acquisitions per pool client. */
    private static final int CLIENT_RUNS = 40;

    /** Duration of a real server bind. */
    private static final long CLIENT_HOLD = 50L;

    /** Maximum child-process wait. */
    private static final long PROCESS_TIMEOUT = 45L;

    /** Crash-test child hold time. */
    private static final long CRASH_HOLD = 30_000L;

    /** Size of the deliberately capacity-constrained pool. */
    private static final int RANGE_SIZE = 4;

    /** First candidate for a deliberately contended pool. */
    private static final int RANGE_START = 20_000;

    /** Last candidate for a deliberately contended pool. */
    private static final int RANGE_END = 29_999;

    @Test
    @Timeout(60)
    void preventsTheRaceThatPlainBindZeroExposes() throws Exception {
        final Path directory = CrossJvmIT.directory("contention");
        final List<Process> churners = CrossJvmIT.churners(directory);
        try {
            CrossJvmIT.awaitChurn(directory, churners);
            CrossJvmIT.positiveControl(directory);
            CrossJvmIT.assertAlive(churners);
            CrossJvmIT.measureControl(directory);
            CrossJvmIT.assertAlive(churners);
            final List<Path> clients = CrossJvmIT.startClients(
                directory, CrossJvmIT.range()
            );
            CrossJvmIT.assertAlive(churners);
            for (final Path result : clients) {
                Assertions.assertEquals(
                    CrossJvmIT.CLIENT_RUNS, Files.readAllLines(result).size(),
                    result.toString()
                );
            }
        } finally {
            CrossJvmIT.stop(churners);
        }
    }

    @Test
    @Timeout(20)
    void recoversTheLockAfterAProcessCrash() throws Exception {
        final Path directory = CrossJvmIT.directory("crash");
        final int port = CrossJvmIT.availablePort();
        final String range = String.format("%d-%d", port, port);
        final Path ready = directory.resolve("ready.txt");
        final Process child = CrossJvmIT.start(
            directory.resolve("holder.log"), "hold", ready.toString(),
            range, Long.toString(CrossJvmIT.CRASH_HOLD)
        );
        try {
            CrossJvmIT.await(ready, child);
            Assertions.assertThrows(
                NoFreePortException.class,
                () -> new Ports(range, 100L).acquire()
            );
            child.destroyForcibly();
            Assertions.assertTrue(child.waitFor(5L, TimeUnit.SECONDS));
            try (
                Reservation reservation = new Ports(range, 4000L).acquire();
                ServerSocket ignored = new ServerSocket(reservation.port())
            ) {
                Assertions.assertEquals(port, reservation.port());
            }
        } finally {
            CrossJvmIT.stop(List.of(child));
        }
    }

    /**
     * Prove the harness catches the plain bind-zero race.
     * @param directory Diagnostics directory
     * @throws Exception When either child fails
     */
    private static void positiveControl(final Path directory) throws Exception {
        final Path offer = directory.resolve("control-offer.txt");
        final Path ready = directory.resolve("control-ready.txt");
        final Path result = directory.resolve("control-result.txt");
        final List<Process> processes = new ArrayList<>(2);
        try {
            final Process owner = CrossJvmIT.start(
                directory.resolve("control-owner.log"), "owner",
                result.toString(), offer.toString(), ready.toString()
            );
            processes.add(owner);
            processes.add(
                CrossJvmIT.start(
                    directory.resolve("control-thief.log"), "thief",
                    offer.toString(), ready.toString(), "5000"
                )
            );
            CrossJvmIT.success(owner, result);
            Assertions.assertEquals("BindException", Files.readString(result));
        } finally {
            CrossJvmIT.stop(processes);
        }
    }

    /**
     * Measure ordinary bind-zero theft under the active churn processes.
     * @param directory Diagnostics directory
     * @throws Exception When a measuring process fails
     */
    private static void measureControl(final Path directory) throws Exception {
        final List<Process> processes = new ArrayList<>(CrossJvmIT.CONTROLS);
        final List<Path> results = new ArrayList<>(CrossJvmIT.CONTROLS);
        boolean complete = false;
        try {
            for (int idx = 0; idx < CrossJvmIT.CONTROLS; idx += 1) {
                final Path result = directory.resolve(
                    String.format("naive-%d.txt", idx)
                );
                results.add(result);
                processes.add(
                    CrossJvmIT.start(
                        directory.resolve(
                            String.format("naive-%d.log", idx)
                        ),
                        "naive", result.toString(),
                        Integer.toString(CrossJvmIT.CONTROL_RUNS),
                        Long.toString(CrossJvmIT.CONTROL_HOLD)
                    )
                );
            }
            int lost = 0;
            int total = 0;
            for (int idx = 0; idx < processes.size(); idx += 1) {
                final Path result = results.get(idx);
                CrossJvmIT.success(processes.get(idx), result);
                final String report = Files.readString(result).trim();
                final int separator = report.indexOf(' ');
                lost += Integer.parseInt(report.substring(0, separator));
                total += Integer.parseInt(report.substring(separator + 1));
            }
            System.out.printf(
                "bind(0) theft on %s: %d/%d%n",
                System.getProperty("os.name"), lost, total
            );
            Assertions.assertEquals(
                CrossJvmIT.CONTROLS * CrossJvmIT.CONTROL_RUNS, total
            );
            if (System.getProperty("os.name").startsWith("Linux")) {
                Assertions.assertTrue(
                    lost > 0, "allocator churn produced no bind(0) theft"
                );
            }
            complete = true;
        } finally {
            if (!complete) {
                CrossJvmIT.stop(processes);
            }
        }
    }

    /**
     * Wait for proof that every churn process has allocated sockets.
     * @param directory Diagnostics directory
     * @param processes Churn processes
     * @throws Exception When churn does not start
     */
    private static void awaitChurn(final Path directory,
        final List<Process> processes) throws Exception {
        for (int idx = 0; idx < processes.size(); idx += 1) {
            final Path heartbeat = directory.resolve(
                String.format("churn-%d.ready", idx)
            );
            CrossJvmIT.await(heartbeat, processes.get(idx));
            Assertions.assertTrue(
                Integer.parseInt(Files.readString(heartbeat)) > 0,
                heartbeat.toString()
            );
        }
    }

    /**
     * Assert every background process is still applying pressure.
     * @param processes Churn processes
     */
    private static void assertAlive(final List<Process> processes) {
        Assertions.assertTrue(
            processes.stream().allMatch(Process::isAlive),
            "an allocator churn process stopped early"
        );
    }

    /**
     * Start operating-system allocator churn.
     * @param directory Diagnostics directory
     * @return Child processes
     * @throws IOException When a child cannot start
     */
    private static List<Process> churners(final Path directory)
        throws IOException {
        final List<Process> processes = new ArrayList<>(CrossJvmIT.CHURNERS);
        try {
            for (int idx = 0; idx < CrossJvmIT.CHURNERS; idx += 1) {
                processes.add(
                    CrossJvmIT.start(
                        directory.resolve(String.format("churn-%d.log", idx)),
                        "churn", Long.toString(CrossJvmIT.CHURN_MILLIS),
                        directory.resolve(
                            String.format("churn-%d.ready", idx)
                        ).toString()
                    )
                );
            }
        } catch (final IOException failure) {
            CrossJvmIT.stop(processes);
            throw failure;
        }
        return processes;
    }

    /**
     * Start and await competing pool clients.
     * @param directory Diagnostics directory
     * @param range Shared narrow range
     * @return Result files
     * @throws Exception When any child fails
     */
    private static List<Path> startClients(final Path directory,
        final String range) throws Exception {
        final List<Process> processes = new ArrayList<>(CrossJvmIT.CLIENTS);
        final List<Path> results = new ArrayList<>(CrossJvmIT.CLIENTS);
        final List<Path> ready = new ArrayList<>(CrossJvmIT.CLIENTS);
        final Path start = directory.resolve("pool-start.ready");
        boolean complete = false;
        try {
            for (int idx = 0; idx < CrossJvmIT.CLIENTS; idx += 1) {
                final Path result = directory.resolve(
                    String.format("pool-%d.txt", idx)
                );
                final Path waiting = directory.resolve(
                    String.format("pool-%d.ready", idx)
                );
                results.add(result);
                ready.add(waiting);
                processes.add(
                    CrossJvmIT.start(
                        directory.resolve(String.format("pool-%d.log", idx)),
                        "pool", result.toString(),
                        Integer.toString(CrossJvmIT.CLIENT_RUNS),
                        Long.toString(CrossJvmIT.CLIENT_HOLD), range,
                        waiting.toString(), start.toString()
                    )
                );
            }
            for (int idx = 0; idx < processes.size(); idx += 1) {
                CrossJvmIT.await(ready.get(idx), processes.get(idx));
            }
            Files.createFile(start);
            for (int idx = 0; idx < processes.size(); idx += 1) {
                CrossJvmIT.success(processes.get(idx), results.get(idx));
            }
            complete = true;
        } finally {
            if (!complete) {
                CrossJvmIT.stop(processes);
            }
        }
        return results;
    }

    /**
     * Locate a free contiguous block outside common ephemeral ranges.
     * @return Inclusive range
     * @throws IOException When probes fail
     */
    private static String range() throws IOException {
        String range = null;
        int first = CrossJvmIT.RANGE_START;
        while (range == null && first <= CrossJvmIT.RANGE_END
            - CrossJvmIT.RANGE_SIZE) {
            final int last = first + CrossJvmIT.RANGE_SIZE - 1;
            boolean block = true;
            for (int port = first; port <= last; port += 1) {
                block = block && CrossJvmIT.available(port);
            }
            if (block) {
                range = String.format("%d-%d", first, last);
            }
            first += CrossJvmIT.RANGE_SIZE;
        }
        if (range == null) {
            throw new IOException("No free integration-test port range");
        }
        return range;
    }

    /**
     * Probe one fixed port.
     * @param port Port number
     * @return Whether it is available
     */
    private static boolean available(final int port) {
        boolean available = true;
        try (ServerSocket ignored = new ServerSocket(port)) {
            ignored.getLocalPort();
        } catch (final IOException occupied) {
            available = false;
        }
        return available;
    }

    /**
     * Start a Java child process.
     * @param log Standard output and error log
     * @param args Worker arguments
     * @return Child process
     * @throws IOException When the child cannot start
     */
    private static Process start(final Path log, final String... args)
        throws IOException {
        final List<String> command = new ArrayList<>(args.length + 5);
        command.add(CrossJvmIT.java());
        command.add("-cp");
        command.add(
            System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
            )
        );
        command.add(CrossJvmWorker.class.getName());
        for (final String argument : args) {
            command.add(argument);
        }
        return new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    }

    /**
     * Assert successful process completion and a result file.
     * @param process Child process
     * @param result Expected result
     * @throws Exception When the child times out or fails
     */
    private static void success(final Process process, final Path result)
        throws Exception {
        final boolean finished = process.waitFor(
            CrossJvmIT.PROCESS_TIMEOUT, TimeUnit.SECONDS
        );
        if (!finished) {
            process.destroyForcibly();
        }
        Assertions.assertTrue(
            finished, String.format("child process timed out: %s", result)
        );
        Assertions.assertEquals(0, process.exitValue(), result.toString());
        Assertions.assertTrue(Files.isRegularFile(result), result.toString());
    }

    /**
     * Wait until a holder has acquired its reservation.
     * @param ready Readiness file
     * @param process Holder process
     * @throws Exception When readiness is not reported
     */
    private static void await(final Path ready, final Process process)
        throws Exception {
        final long deadline = System.currentTimeMillis() + 5000L;
        while (!Files.isRegularFile(ready)
            && process.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
        Assertions.assertTrue(Files.isRegularFile(ready), ready.toString());
    }

    /**
     * Stop background children.
     * @param processes Child processes
     */
    private static void stop(final List<Process> processes) {
        for (final Process process : processes) {
            process.destroyForcibly();
        }
        for (final Process process : processes) {
            try {
                process.waitFor(5L, TimeUnit.SECONDS);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Create a clean diagnostic directory.
     * @param name Test name
     * @return Directory
     * @throws IOException When it cannot be created
     */
    private static Path directory(final String name) throws IOException {
        final Path root = Path.of("target", "e2e-logs").toAbsolutePath();
        Files.createDirectories(root);
        return Files.createTempDirectory(root, String.format("%s-", name));
    }

    /**
     * Find a currently available fixed-range port.
     * @return Port number
     * @throws IOException When the probe fails
     */
    private static int availablePort() throws IOException {
        int available = 0;
        int candidate = CrossJvmIT.RANGE_START;
        while (available == 0 && candidate <= CrossJvmIT.RANGE_END) {
            if (CrossJvmIT.available(candidate)) {
                available = candidate;
            }
            candidate += 1;
        }
        if (available == 0) {
            throw new IOException("No free integration-test port");
        }
        return available;
    }

    /**
     * Locate the current JVM executable without shell assumptions.
     * @return Executable path
     */
    private static String java() {
        final String executable;
        if (System.getProperty("os.name").startsWith("Windows")) {
            executable = "java.exe";
        } else {
            executable = "java";
        }
        return Path.of(
            System.getProperty("java.home"), "bin", executable
        ).toString();
    }
}
