# Contributing to Keycloak IdP Discovery

Thanks for your interest in improving Keycloak IdP Discovery.

## Build

```bash
mvn package    # build + unit tests, against the pinned Keycloak version (see pom.xml)
```

JDK 21 is used to build; the jar targets bytecode 17. This extends Keycloak's
internal implementation classes, not just the public SPI, so it is pinned to a
single Keycloak version (currently 26.7.3) rather than a version range - see
[VERSIONING.md](./VERSIONING.md). Don't override `-Dkeycloak.version` expecting
it to compile; it won't.

## Pull requests

- Keep changes focused: one concern per PR.
- Add or update tests for any behavior change.
- Use conventional-commit messages (`feat`, `fix`, `docs`, `chore`, ...).
- Make sure `mvn package` passes and the change has been verified live against a
  real Keycloak before opening the PR.

## Reporting bugs

Open an issue with your Keycloak version, the extension version, your flow shape,
and steps to reproduce. For security issues, see [SECURITY.md](SECURITY.md) instead.
