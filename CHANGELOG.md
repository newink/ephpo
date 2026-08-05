# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
During the `0.x` series, minor releases may change the API; patch releases
remain compatible within their minor line, as stated in
[RELEASING.md](RELEASING.md).

## [Unreleased]

### Changed

- JUnit moved to 5.14.4 and is now managed through `junit-bom`, so one version
  covers every Jupiter and Platform artifact. The 5.x line is the last one that
  keeps the Java 11 baseline this library promises; JUnit 6 requires Java 17,
  so Dependabot is told to skip major JUnit updates until the baseline moves.

### Fixed

- Reservations parked in the JUnit store are released by every engine from
  5.1 onwards. They used to implement only `CloseableResource`, deprecated in
  JUnit 5.13, which made engines from that version on log a warning about it;
  they now implement `AutoCloseable` as well.

## [0.1.1] - 2026-08-05

### Added

- `@API` stability annotations on every public type, so tools and users can
  tell what is stable.
- `Automatic-Module-Name: codes.ivanov.ephpo`, making the jar usable as a
  named automatic module on the module path.
- A reproducible `project.build.outputTimestamp`, so identical sources produce
  a byte-identical jar.
- An `ExtensionConfigurationException` when an `@Ephemeral` field is injected
  into a concurrent `PER_CLASS` test instance, where one instance is shared by
  threads and the field cannot hold a per-invocation port. Parameter
  injection, `PER_METHOD`, and `@Execution(SAME_THREAD)` are unaffected.
- Documentation of `ephpo.range` and `ephpo.timeout` for Maven and Gradle, of
  the parallel-execution contract, and of the registry file.

### Changed

- `Ports` is now an envelope composing `Patient`, `Bindable`, `Candidates`,
  and `Registry`. Behaviour and the public API are unchanged.
- The coverage gate runs on Java 11, 17, 21, and 25 instead of Java 11 alone.

### Deprecated

- `Pool.SINGLETON`. A pool holds no state worth sharing, so construct `Ports`
  where you need it. Scheduled for removal in 0.2.0.

### Fixed

- Releasing one reservation no longer releases the ports other reservations
  hold in the same JVM. POSIX drops every lock a process holds on a file as
  soon as that process closes any descriptor for it, so all reservations now
  share one reference-counted channel per registry file, closed only with the
  last of them.

## [0.1.0] - 2026-08-04

### Added

- Initial release: cross-process TCP port reservations for JUnit 5.
- `@Ephemeral` injection of `int` and `Integer` parameters and instance
  fields, through the `EphemeralResolver` extension, registered explicitly or
  auto-detected through the ServiceLoader.
- The `Pool`, `Ports`, and `Reservation` API for use outside JUnit.
- Host-wide coordination through byte-range locks in a single registry file
  under `${java.io.tmpdir}/ephpo`, with every candidate verified bindable on
  every local interface before it is handed out.
- `ephpo.range` and `ephpo.timeout` system properties, defaulting to
  `20000-29999` and `4000` milliseconds.
- `NoFreePortException` with actionable diagnostics when a range is exhausted.

[Unreleased]: https://github.com/newink/ephpo/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/newink/ephpo/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/newink/ephpo/releases/tag/v0.1.0
