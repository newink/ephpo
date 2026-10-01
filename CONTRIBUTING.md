# Contributing to ephpo

Thank you for considering a contribution. Bug reports, questions, and pull
requests are all welcome.

## Before you write code

Open an issue first for anything larger than a typo. ephpo has a small,
deliberately narrow API, and it is easier to agree on a change before it is
written than to reject a finished pull request.

## Build and test

Java 11 or newer and Git are all you need; the Maven Wrapper takes care of
Maven itself. The three commands below are exactly what CI runs, so a branch
that passes them locally passes the gate:

```shell
./mvnw -Pqulice clean verify      # static analysis, must be run on Java 21+
./mvnw -Pcoverage clean verify    # unit tests and the JaCoCo report
./mvnw -Pe2e clean verify         # unit plus cross-JVM integration tests
```

The library itself compiles to Java 11 bytecode
(`maven.compiler.release=11`), and CI runs the tests on Java 11, 17, 21, and
25. The `e2e` profile spawns real child JVMs that bind real TCP ports; it
takes about half a minute.

## The Qulice gate

**Read this section before writing code.** ephpo enforces
[Qulice](https://www.qulice.com), a strict aggregate of Checkstyle and PMD.
It is not advisory: `./mvnw -Pqulice verify` fails the build on the first
violation, and the pull-request gate blocks a merge until it passes. Qulice is
much stricter than the defaults most Java projects use, so please run it
before you push rather than after.

The rules that catch newcomers most often:

- Lines are at most 100 characters, including Javadoc and comments.
- Classes, constructors, and fields need Javadoc, as do methods that are
  neither private nor overrides. Use ordinary comments for private methods.
  Every class and interface needs an `@since` tag with its first version.
- Inline private constants used only once. Put static nested classes in
  their own files.
- Parameters and local variables must be `final`.
- Instance members are addressed through `this.`, static members through the
  class name — `this.pool`, `Registry.LOG`.
- A method has one `return` at the end. Assign to a single result variable and
  return it once instead of returning from several branches.
- No static mutable state, no utility classes, no `null` returns; prefer
  `Optional`.
- Suppressions are allowed, but each one must be justified. Put
  `@SuppressWarnings("PMD.RuleName")` on the narrowest scope that works, and
  do not suppress a rule you could satisfy instead.

If a rule seems wrong for a particular case, say so in the pull request — an
argued suppression is fine, a silent one is not.

## Design

The code follows [Elegant Objects](https://www.elegantobjects.org). In
practice, for this codebase:

- Each class does one thing and is named after what it is, not what it does —
  `Registry`, `Held`, `Candidates`, `Patient`.
- Constructors only assign fields. Work happens in methods.
- Compose behaviour with decorators rather than growing a class. `Ports` is an
  envelope over `Patient(Bindable(Candidates(...)))`; adding a capability
  usually means adding a decorator, not a branch.
- Tests use fakes, not mocks. `FakeReservation` is the pattern to follow.

## Tests

Every behaviour change needs a test, and the coverage gate has to stay green.
Two rules of thumb:

- Test through the public API when you can; reach for a package-private class
  only when the behaviour is not observable from outside.
- Make sure the test can fail. Break the production line you just covered,
  watch the new test go red, then restore it. A test that passes against
  broken code is worse than no test.

Cross-process behaviour belongs in `CrossJvmIT`, which runs under Failsafe in
the `e2e` profile. Anything that must hold across JVMs — and port reservation
is exactly that — cannot be proven inside a single JVM.

## Commits and pull requests

- One logical change per commit, with a subject in the imperative mood ("Add
  the registry", not "Added" or "Adds"), no trailing period, 50 characters or
  fewer. Explain *why* in the body if it is not obvious.
- Rebase on `master` rather than merging it into your branch.
- Fill in the pull-request template and note which of the three commands above
  you ran locally.
- Update `CHANGELOG.md` under `## [Unreleased]` for anything a user would
  notice.

By contributing, you agree that your work is licensed under the
[MIT License](LICENSE.txt).
