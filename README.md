# Keycloak IdP Discovery

Redirects a user straight to their linked identity provider during login, without a password
prompt in between, on realms where the username step and the password step are two **separate**
pages rather than one combined form.

## Why this exists

Most "home IdP discovery" tooling for Keycloak, including the well-known
[`sventorben/keycloak-home-idp-discovery`](https://github.com/sventorben/keycloak-home-idp-discovery)
extension, is built to sit next to Keycloak's combined username-and-password form. That's the
right choice for most realms. It does not work for a **split, identity-first** flow, where
username is its own step and password (or a passkey) is a separate, later one: per the
maintainer's own explanation on
[sventorben/keycloak-home-idp-discovery#285](https://github.com/sventorben/keycloak-home-idp-discovery/issues/285),
that extension expects the combined form and will reject normal password logins if wired in for
just the username step.

An account that only has a linked identity provider (Google, SAML, whatever) and no local
password or passkey has no way to sign in on a split flow otherwise: it reaches the password
step with nothing to check, and gets a dead-end "credential setup required" error.

This extension does one thing, built for the split case:

- **Renders the exact same username-collection page** Keycloak's stock `auth-username-form`
  already renders, with the exact same username/email resolution behavior. It's a drop-in
  replacement for that authenticator, not an extra step in front of it.
- **After resolving the user, checks whether they already have a linked identity provider.** If
  they have exactly one, redirects there immediately, skipping password/passkey entirely.
- **If they have none, or more than one, falls through exactly like `auth-username-form`
  would** and the flow continues to whatever comes next (password, passkey, etc.),
  unchanged. Picking one of several linked providers isn't this extension's call to
  make silently, so ambiguous cases are left to the rest of the flow.

No domain matching, no email-verification gating, no configuration surface. If it becomes
necessary later, it can grow one, deliberately kept out of the first version.

## Install

Download `keycloak-idp-discovery.jar` from the [Releases](https://github.com/sky-cloak/keycloak-idp-discovery/releases)
page and copy it into Keycloak's providers directory:

```bash
cp keycloak-idp-discovery.jar /opt/keycloak/providers/
```

Or pull the published OCI artifact into a Dockerfile build:

```dockerfile
COPY --from=ghcr.io/sky-cloak/keycloak-idp-discovery:vX.Y.Z /jars/*.jar /opt/keycloak/providers/
```

Restart Keycloak (or run `kc.sh build` first, if you're baking a custom image) so it picks up the
new provider.

## Flow setup

In your realm's browser flow, find the execution using `auth-username-form` (the stock username
step) and swap its provider for **IdP Discovery**, keeping it at the same requirement
(`REQUIRED`, typically) and the same position. No other changes to the flow are needed: password,
passkey, and second-factor steps after it are untouched.

## Keycloak compatibility

Built against and **pinned to Keycloak 26.6.3 only**. This extends Keycloak's internal
implementation classes (`UsernamePasswordForm`, `IdentityProviderAuthenticator`) for correctness
rather than reimplementing username/password/broker-redirect logic from scratch, and those
internal classes genuinely differ across 26.x patch releases - confirmed, this does not compile
against 26.2.0 without changes. See [VERSIONING.md](./VERSIONING.md).

---

Built and maintained by [Skycloak](https://skycloak.io).
