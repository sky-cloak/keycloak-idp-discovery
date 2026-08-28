# Contributing to Keycloak IdP Discovery

Thanks for your interest in improving Keycloak IdP Discovery.

## Build

```bash
mvn package                              # build + unit tests
mvn -Dkeycloak.version=26.2.0 package    # build against a specific Keycloak version
```

JDK 21 is used to build; the jar targets bytecode 17 and runs on Keycloak 26.

## Pull requests

- Keep changes focused: one concern per PR.
- Add or update tests for any behavior change.
- Use conventional-commit messages (`feat`, `fix`, `docs`, `chore`, ...).
- Make sure `mvn package` passes and the change has been verified live against a
  real Keycloak before opening the PR.

## Reporting bugs

Open an issue with your Keycloak version, the extension version, your flow shape,
and steps to reproduce. For security issues, see [SECURITY.md](SECURITY.md) instead.
