# ephpo

[![CI](https://github.com/newink/ephpo/actions/workflows/ci.yml/badge.svg?branch=master&event=push)](https://github.com/newink/ephpo/actions/workflows/ci.yml)
[![Qulice](https://img.shields.io/github/check-runs/newink/ephpo/master?nameFilter=Quality%20%2F%20Qulice&label=Qulice)](https://github.com/newink/ephpo/actions/workflows/ci.yml)
[![Test Coverage](https://codecov.io/gh/newink/ephpo/branch/master/graph/badge.svg)](https://codecov.io/gh/newink/ephpo)
[![Maven Central](https://img.shields.io/maven-central/v/codes.ivanov/ephpo.svg)](https://central.sonatype.com/artifact/codes.ivanov/ephpo)
[![Java 11+](https://img.shields.io/badge/Java-11%2B-007396?logo=openjdk&logoColor=white)](#install)

Cross-process TCP port reservations for JUnit 5 tests.

Use ephpo when a test must know a port before a server or child process binds
it. If the server can bind to port `0` and report the selected port, prefer
that simpler approach.

## Install

Add ephpo as a test dependency:

Maven:

```xml
<dependency>
  <groupId>codes.ivanov</groupId>
  <artifactId>ephpo</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

Gradle:

```groovy
testImplementation 'codes.ivanov:ephpo:0.1.0'
```

Java 11 or newer and JUnit 5 are required.

## Use

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

`@Ephemeral` supports `int` and `Integer` parameters and instance fields.
Field values are available in `@BeforeEach`; reservations are released after
each test invocation.

Parallel execution is a first-class use case. With
`junit.jupiter.execution.parallel.enabled=true`, ephpo coordinates port
reservations across JUnit worker threads and across multiple Maven or Gradle
test JVMs running simultaneously on the same host, so cooperating forks never
receive the same port. Parameter injection and `PER_METHOD` field injection
support this mode. Concurrent `PER_CLASS` tests cannot use `@Ephemeral` fields;
use parameter injection or `@Execution(SAME_THREAD)` instead.

To register the extension automatically, add
`src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

Then `@ExtendWith(EphemeralResolver.class)` is not needed.

The reservation API can also be used directly:

```java
try (Reservation reservation = new Ports().acquire()) {
    int port = reservation.port();
    // Start the server or child process while the reservation is open.
}
```

A pool holds no state worth sharing, so construct `Ports` wherever you need
it. `Pool.SINGLETON` still works but is deprecated and will be removed in
0.2.0.

## Configure

The default range is `20000-29999` and the default acquisition timeout is
`4000` milliseconds. Set `ephpo.range` and `ephpo.timeout` on the **test JVM**
before ephpo is initialized. For a Maven command, Surefire forwards user
properties to its test JVM by default:

```shell
./mvnw -Dephpo.range=30000-30999 -Dephpo.timeout=8000 test
```

For persistent Maven configuration, use Surefire's system properties:

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

For Gradle, configure the forked `Test` task explicitly:

```groovy
tasks.named('test') {
    systemProperty 'ephpo.range', '30000-30999'
    systemProperty 'ephpo.timeout', '8000'
}
```

Passing `-D` to `gradlew` only configures the Gradle JVM; it does not
automatically configure the test worker. Do not rely on `System.setProperty`
inside a test or lifecycle method: the pool reads both values when JUnit
instantiates the extension, which may already have happened. Code using the
pool directly can avoid global properties with
`new Ports("30000-30999", 8000L)`.

The range must cover the peak number of ports reserved concurrently. The
default provides 10,000 ports; expand the range if it is exhausted.

Cross-process coordination uses byte-range locks in one zero-length registry,
`${java.io.tmpdir}/ephpo/registry.lock`. The file is reused and may remain after
the JVM exits.

ephpo prevents cooperating JVMs from receiving the same port and skips ports
already in use. It cannot prevent an unrelated process from deliberately
binding a reserved port.

## Contribute

Bug reports and pull requests are welcome. Read
[CONTRIBUTING.md](CONTRIBUTING.md) first: the build is gated on Qulice, which
is stricter than most Java projects, and it is cheaper to know that before you
write code than after. Released changes are listed in
[CHANGELOG.md](CHANGELOG.md).

Participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).
Vulnerabilities go to [SECURITY.md](SECURITY.md), never to a public issue.

Licensed under the [MIT License](LICENSE.txt).
