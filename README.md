# ephpo

[![CI](https://github.com/newink/ephpo/actions/workflows/ci.yml/badge.svg?branch=master&event=push)](https://github.com/newink/ephpo/actions/workflows/ci.yml)
[![Qulice](https://img.shields.io/github/check-runs/newink/ephpo/master?nameFilter=Quality%20%2F%20Qulice&label=Qulice)](https://github.com/newink/ephpo/actions/workflows/ci.yml)
[![Test coverage](https://codecov.io/gh/newink/ephpo/branch/master/graph/badge.svg)](https://codecov.io/gh/newink/ephpo)
[![Maven Central](https://img.shields.io/maven-central/v/codes.ivanov/ephpo.svg)](https://central.sonatype.com/artifact/codes.ivanov/ephpo)
[![Java 11+](https://img.shields.io/badge/Java-11%2B-007396?logo=openjdk&logoColor=white)](#install)

Reserve TCP ports for JUnit 5 tests before starting a server or child process.
File locks keep tests in cooperating JVMs from choosing the same port.

If your server can bind to port `0` and report its port, use that. ephpo is
for tests that need the port number before the server starts.

## Install

Requires Java 11 or newer and JUnit 5. Add ephpo as a test dependency.

Maven:

```xml
<dependency>
  <groupId>codes.ivanov</groupId>
  <artifactId>ephpo</artifactId>
  <version>1.0.1</version>
  <scope>test</scope>
</dependency>
```

Gradle:

```groovy
testImplementation 'codes.ivanov:ephpo:1.0.1'
```

## Use with JUnit

Register `EphemeralResolver` and annotate the parameter that needs a port:

```java
import codes.ivanov.ephpo.Ephemeral;
import codes.ivanov.ephpo.EphemeralResolver;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralResolver.class)
final class ServerTest {

    @Test
    void bindsReservedPort(@Ephemeral final int port) throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            assertEquals(port, server.getLocalPort());
        }
    }
}
```

`@Ephemeral` accepts `int` and `Integer` parameters and instance fields.
The extension sets fields before `@BeforeEach` and releases reservations
after each test invocation.

For automatic registration, add this to
`src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

You can then omit `@ExtendWith`.

## Parallel tests

ephpo coordinates reservations across JUnit threads and Maven or Gradle test
forks on the same host. The JVMs must use the same registry file.

Set `junit.jupiter.execution.parallel.enabled=true` to enable JUnit's
parallel execution support.

When JUnit runs tests concurrently, parameter injection works with either
`PER_METHOD` or `PER_CLASS`. Field injection requires `PER_METHOD`, because
each test needs its own instance. For `PER_CLASS` tests with annotated fields,
use `@Execution(SAME_THREAD)`.

## Use without JUnit

Acquire a reservation and keep it open while the server uses the port:

```java
import codes.ivanov.ephpo.Ports;
import codes.ivanov.ephpo.Reservation;
import java.net.ServerSocket;

// Inside a test method:
try (
    Reservation reservation = new Ports().acquire();
    ServerSocket server = new ServerSocket(reservation.port())
) {
    // Exercise the server here.
}
```

Closing the reservation releases its file lock. You can create a `Ports`
instance wherever you need one. `Pool.SINGLETON` is deprecated and scheduled
for removal in 2.0.0.

## Configure the test JVM

| Property | Default | Meaning |
| --- | --- | --- |
| `ephpo.range` | `20000-29999` | Inclusive range of TCP ports |
| `ephpo.timeout` | `4000` | Acquisition timeout in milliseconds |

Set these properties before JUnit creates the extension. Setting them in a
test or lifecycle method can be too late, because the pool reads them when
the extension is constructed.

Surefire forwards Maven command-line properties to the test JVM by default:

```shell
./mvnw -Dephpo.range=30000-30999 -Dephpo.timeout=8000 test
```

To keep the configuration in your POM:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <systemPropertyVariables>
      <ephpo.range>30000-30999</ephpo.range>
      <ephpo.timeout>8000</ephpo.timeout>
    </systemPropertyVariables>
  </configuration>
</plugin>
```

For Gradle, set the properties on the `Test` task:

```groovy
tasks.named('test') {
    systemProperty 'ephpo.range', '30000-30999'
    systemProperty 'ephpo.timeout', '8000'
}
```

Passing `-D` to `gradlew` sets a property on the Gradle JVM. It does not
forward that property to the test worker.

With the reservation API, pass the range and timeout directly:

```java
new Ports("30000-30999", 8000L)
```

Choose a range large enough for the peak number of simultaneous reservations.
The default range contains 10,000 ports. If acquisition fails with
`NoFreePortException`, check whether the range is full or its ports are in
use, then increase the range or timeout as needed.

## How reservations work

Each reservation holds a byte-range lock in
`${java.io.tmpdir}/ephpo/registry.lock`. The file stays empty and can remain
after the JVM exits. Cooperating JVMs must share this file to coordinate
their reservations.

ephpo checks that a candidate port can be bound before returning it. The
reservation holds a file lock, so an unrelated process can still bind the
port before your server does.

## Contribute

Read [CONTRIBUTING.md](CONTRIBUTING.md) for build commands and Qulice rules.
CI blocks merges when those checks fail.

See [CHANGELOG.md](CHANGELOG.md) for released changes and the
[Code of Conduct](CODE_OF_CONDUCT.md) for participation rules. Report
vulnerabilities through [SECURITY.md](SECURITY.md).

Licensed under the [MIT License](LICENSE.txt).
