# ephpo

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

To register the extension automatically, add
`src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

Then `@ExtendWith(EphemeralResolver.class)` is not needed.

The reservation API can also be used directly:

```java
try (Reservation reservation = Pool.SINGLETON.acquire()) {
    int port = reservation.port();
    // Start the server or child process while the reservation is open.
}
```

The default range is `20000-29999` and the default acquisition timeout is
`4000` milliseconds. Override them with `-Dephpo.range=MIN-MAX` and
`-Dephpo.timeout=MILLISECONDS`.

ephpo prevents cooperating JVMs from receiving the same port and skips ports
already in use. It cannot prevent an unrelated process from deliberately
binding a reserved port.

Licensed under the [MIT License](LICENSE.txt).
