# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to the versioning
policy in [VERSIONING.md](./VERSIONING.md).

## [0.1.0]

MVP.

- `IdpDiscoveryAuthenticator`: drop-in replacement for `auth-username-form` in a split
  (identity-first) browser flow. Redirects to a resolved user's linked identity provider when
  exactly one exists; falls through to the next flow execution unchanged otherwise.
- No configuration surface in this version.
- Pinned to Keycloak 26.6.3 only (see [VERSIONING.md](./VERSIONING.md)); no older-version
  compile check in CI, since this extends internal Keycloak classes that do not compile
  against 26.2.0 unchanged.
