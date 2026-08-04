# Releasing ephpo

Releases are immutable and are published only from commits already merged into
`master`. During the `0.x` series, minor releases may change the API; patch
releases remain compatible within their minor line.

## One-time setup

The `maven-central` GitHub Environment holds four secrets:

- `CENTRAL_TOKEN_USERNAME`
- `CENTRAL_TOKEN_PASSWORD`
- `MAVEN_GPG_PRIVATE_KEY`
- `MAVEN_GPG_PASSPHRASE`

The Central Portal namespace is `codes.ivanov`. The public signing key is
published to `keyserver.ubuntu.com`; its fingerprint is
`09AA 2C24 CA62 4EAE DD63 C512 132C C9B3 9DFB CF38`. The private key and
passphrase are GitHub Environment secrets; the passphrase is also backed up in
the macOS Keychain item `ephpo-maven-gpg-passphrase`.

## Release

1. Set the release version in `pom.xml`, update documentation if needed, and
   merge the change through the normal pull-request quality gate.
2. Tag the release commit from an up-to-date `master`:

   ```bash
   git switch master
   git pull --ff-only
   git tag -a v0.1.0 -m "ephpo 0.1.0"
   git push origin v0.1.0
   ```

3. Approve the `maven-central` deployment in GitHub Actions. The workflow
   verifies the tag, reruns the complete quality matrix, signs every artifact,
   and waits until Central reports the deployment as published.
4. Change `pom.xml` to the next development version, for example
   `0.1.1-SNAPSHOT`, in a new pull request.

If a release fails before Central reports `published`, fix the configuration
and rerun the failed workflow. Published coordinates cannot be replaced or
deleted; publish a new patch version instead.
