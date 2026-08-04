# Security Policy

## Supported versions

ephpo is released from `master` only. Fixes go into the next release of the
current minor line; older lines are not patched.

| Version | Supported |
| ------- | --------- |
| 0.1.x   | Yes       |
| < 0.1   | No        |

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Report it privately through GitHub:
[Security → Report a vulnerability](https://github.com/newink/ephpo/security/advisories/new).
If you cannot use GitHub, email <mail@ivanov.codes> instead.

Please include the affected version, your operating system and JDK, and the
smallest reproducer you can manage. You will get an acknowledgement within
7 days and an assessment within 30 days. If the report is valid, you will be
credited in the advisory and the changelog unless you prefer otherwise.

## Scope

ephpo is a test-scoped library. It reserves TCP ports by taking byte-range
locks in `${java.io.tmpdir}/ephpo/registry.lock` and returns them to
cooperating JVMs on the same host.

In scope, among others:

- a reservation handed to two callers at once, in one JVM or across JVMs
- the registry file being usable to read or write data outside its own lock
  regions, or to escalate privileges through its path
- a reservation surviving the process that took it, in a way that permanently
  denies a port to later runs

Out of scope:

- an unrelated process deliberately binding a port ephpo reserved. ephpo
  coordinates cooperating processes; it cannot defend a port against a hostile
  one, and the README says so.
- shared-temporary-directory scenarios on a multi-user host. `java.io.tmpdir`
  is trusted; point `java.io.tmpdir` at a private directory if that assumption
  does not hold for you.
- vulnerabilities in JUnit, Maven, or the JDK. Report those upstream.
