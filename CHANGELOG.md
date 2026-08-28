# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to the versioning
policy in [VERSIONING.md](./VERSIONING.md).

## [0.1.1] - 2026-08-28

Fixes found by adversarial review before going public.

- **Fix:** the re-authentication path (a user already resolved in the auth
  session, e.g. right after brokering in) no longer redirects unconditionally to
  the user's sole linked identity provider. It now excludes the IdP that just
  brokered them in first, mirroring stock `UsernameForm`'s `BROKERED_CONTEXT_NOTE`
  filtering, so a user with exactly one linked IdP can't be redirect-looped back
  to it.
- **Fix:** `getOptionalReferenceCategories()` now reports the passwordless
  category when passkeys are enabled, matching stock `UsernameFormFactory` (used
  by LoA-aware flows).
- CI: `release.yml` no longer moves the `:latest` GHCR tag on a prerelease tag,
  and now publishes `linux/amd64,linux/arm64` (was amd64-only, which broke local
  builds on Apple Silicon).
- CI: `ci.yml` declares an explicit read-only `permissions:` block.
- Docs: `CONTRIBUTING.md`'s build example no longer references the unsupported
  `26.2.0` override; `SECURITY.md` and the README now consistently say
  26.6.3-only; the README also documents the "more than one linked IdP" case,
  not just the zero case.

## [0.1.0] - 2026-08-28

MVP.

- `IdpDiscoveryAuthenticator`: drop-in replacement for `auth-username-form` in a split
  (identity-first) browser flow. Redirects to a resolved user's linked identity provider when
  exactly one exists; falls through to the next flow execution unchanged otherwise.
- No configuration surface in this version.
- Pinned to Keycloak 26.6.3 only (see [VERSIONING.md](./VERSIONING.md)); no older-version
  compile check in CI, since this extends internal Keycloak classes that do not compile
  against 26.2.0 unchanged.
