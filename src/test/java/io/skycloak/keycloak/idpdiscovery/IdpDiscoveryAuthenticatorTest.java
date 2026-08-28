package io.skycloak.keycloak.idpdiscovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.keycloak.models.FederatedIdentityModel;

class IdpDiscoveryAuthenticatorTest {

    @Test
    void continuesWhenUserHasNoLinkedIdentityProviders() {
        IdpDiscoveryAuthenticator.LinkedIdentityProviderSelection selection =
                IdpDiscoveryAuthenticator.selectLinkedIdentityProvider(Stream.empty(), null);

        assertNull(selection.providerAlias());
        assertFalse(selection.ambiguous());
    }

    @Test
    void redirectsWhenUserHasExactlyOneLinkedIdentityProvider() {
        IdpDiscoveryAuthenticator.LinkedIdentityProviderSelection selection =
                IdpDiscoveryAuthenticator.selectLinkedIdentityProvider(
                        Stream.of(new FederatedIdentityModel("google", "external-id", "person@example.test")), null);

        assertEquals("google", selection.providerAlias());
        assertFalse(selection.ambiguous());
    }

    @Test
    void continuesWhenUserHasMoreThanOneLinkedIdentityProvider() {
        IdpDiscoveryAuthenticator.LinkedIdentityProviderSelection selection =
                IdpDiscoveryAuthenticator.selectLinkedIdentityProvider(Stream.of(
                        new FederatedIdentityModel("google", "google-id", "person@example.test"),
                        new FederatedIdentityModel("saml", "saml-id", "person@example.test")), null);

        assertNull(selection.providerAlias());
        assertTrue(selection.ambiguous());
    }

    @Test
    void fallsThroughInsteadOfLoopingWhenOnlyLinkedProviderIsTheOneJustBrokeredIn() {
        IdpDiscoveryAuthenticator.LinkedIdentityProviderSelection selection =
                IdpDiscoveryAuthenticator.selectLinkedIdentityProvider(
                        Stream.of(new FederatedIdentityModel("google", "external-id", "person@example.test")),
                        "google");

        assertNull(selection.providerAlias());
        assertFalse(selection.ambiguous());
    }

    @Test
    void redirectsToTheOtherProviderWhenExcludingTheOneJustBrokeredIn() {
        IdpDiscoveryAuthenticator.LinkedIdentityProviderSelection selection =
                IdpDiscoveryAuthenticator.selectLinkedIdentityProvider(Stream.of(
                        new FederatedIdentityModel("google", "google-id", "person@example.test"),
                        new FederatedIdentityModel("saml", "saml-id", "person@example.test")), "google");

        assertEquals("saml", selection.providerAlias());
        assertFalse(selection.ambiguous());
    }
}
