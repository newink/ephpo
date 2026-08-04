## Summary

<!-- What changes, and why. Link the issue it closes: "Closes #123". -->

## Local verification

<!-- Tick what you ran. CI runs all three; a red gate wastes your time and mine. -->

- [ ] `./mvnw -Pqulice clean verify`
- [ ] `./mvnw -Pcoverage clean verify`
- [ ] `./mvnw -Pe2e clean verify`

## Checklist

- [ ] Every behaviour change has a test, and I checked the test fails without
      the change
- [ ] Public API changes are annotated with `@API` and documented in the
      README
- [ ] `CHANGELOG.md` has an entry under `## [Unreleased]`, or this change is
      invisible to users
- [ ] Any new `@SuppressWarnings` is justified in the code or below
