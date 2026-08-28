package io.skycloak.keycloak.idpdiscovery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.credential.PasswordCredentialModel;

class IdpDiscoveryAuthenticatorFactoryTest {

    private final IdpDiscoveryAuthenticatorFactory factory = new IdpDiscoveryAuthenticatorFactory();

    @Test
    void exposesStableMvpMetadata() {
        assertEquals("idp-discovery", factory.getId());
        assertEquals("IdP Discovery", factory.getDisplayType());
        assertEquals(PasswordCredentialModel.TYPE, factory.getReferenceCategory());
        assertFalse(factory.isConfigurable());
        assertFalse(factory.isUserSetupAllowed());
        assertTrue(factory.getConfigProperties().isEmpty());
    }

    @Test
    void supportsRequiredAndDisabledExecutions() {
        assertArrayEquals(new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        }, factory.getRequirementChoices());
    }
}
