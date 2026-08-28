package io.skycloak.keycloak.idpdiscovery;

import java.util.List;
import java.util.stream.Stream;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.browser.IdentityProviderAuthenticator;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.messages.Messages;

/**
 * The username-only half of a split browser flow, with a redirect for users
 * linked to exactly one external identity provider.
 */
public final class IdpDiscoveryAuthenticator extends UsernamePasswordForm {

    private static final Logger LOG = Logger.getLogger(IdpDiscoveryAuthenticator.class);

    private static final IdpRedirector IDP_REDIRECTOR = new IdpRedirector();

    public IdpDiscoveryAuthenticator(KeycloakSession session) {
        super(session);
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (context.getUser() != null) {
            redirectOrContinue(context, false);
            return;
        }

        // UsernamePasswordForm supplies the stock login-hint, remember-me, and
        // conditional-passkey setup. challenge(...) below changes only its
        // rendered template to the stock username-only template.
        super.authenticate(context);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        if (formData.containsKey("cancel")) {
            context.cancelLogin();
            return;
        }

        // This is the same passkey submission branch used by UsernamePasswordForm
        // (and therefore UsernameForm) in Keycloak 26.6.3.
        if (webauthnAuth != null && webauthnAuth.isPasskeysEnabled()
                && (formData.containsKey(WebAuthnConstants.AUTHENTICATOR_DATA)
                || formData.containsKey(WebAuthnConstants.ERROR))) {
            webauthnAuth.action(context);
            return;
        }

        // validateUser is inherited from AbstractUsernameFormAuthenticator. It
        // preserves stock username/email lookup, brute-force, enabled-user, and
        // remember-me handling before it sets the resolved context user.
        if (!validateUser(context, formData)) {
            return;
        }

        redirectOrContinue(context, true);
    }

    @Override
    protected Response challenge(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        LoginFormsProvider forms = context.form();
        if (!formData.isEmpty()) {
            forms.setFormData(formData);
        }
        return forms.createLoginUsername();
    }

    @Override
    protected Response createLoginForm(LoginFormsProvider form) {
        return form.createLoginUsername();
    }

    @Override
    protected String getDefaultChallengeMessage(AuthenticationFlowContext context) {
        return context.getRealm().isLoginWithEmailAllowed()
                ? Messages.INVALID_USERNAME_OR_EMAIL
                : Messages.INVALID_USERNAME;
    }

    private void redirectOrContinue(AuthenticationFlowContext context, boolean resolvedFromForm) {
        LinkedIdentityProviderSelection selection = linkedIdentityProviderSelection(
                context.getSession(), context.getRealm(), context.getUser());
        if (selection.providerAlias() != null) {
            IDP_REDIRECTOR.redirectTo(context, selection.providerAlias());
            return;
        }
        if (selection.ambiguous()) {
            LOG.debugf("User %s has more than one linked identity provider; continuing the browser flow",
                    context.getUser().getId());
        }

        // UsernameForm inherits UsernamePasswordForm.action(), whose successful
        // form path calls success with the password credential category.
        if (resolvedFromForm) {
            context.success(PasswordCredentialModel.TYPE);
        } else {
            context.success();
        }
    }

    static LinkedIdentityProviderSelection selectLinkedIdentityProvider(Stream<FederatedIdentityModel> identities) {
        List<FederatedIdentityModel> firstTwo = identities.limit(2).toList();
        if (firstTwo.size() == 1) {
            return new LinkedIdentityProviderSelection(firstTwo.get(0).getIdentityProvider(), false);
        }
        return new LinkedIdentityProviderSelection(null, firstTwo.size() > 1);
    }

    private static LinkedIdentityProviderSelection linkedIdentityProviderSelection(
            KeycloakSession session, org.keycloak.models.RealmModel realm, UserModel user) {
        try (Stream<FederatedIdentityModel> identities = session.users()
                .getFederatedIdentitiesStream(realm, user)) {
            return selectLinkedIdentityProvider(identities);
        }
    }

    record LinkedIdentityProviderSelection(String providerAlias, boolean ambiguous) {
    }

    /** Exposes the stock redirector's protected redirect implementation. */
    private static final class IdpRedirector extends IdentityProviderAuthenticator {
        void redirectTo(AuthenticationFlowContext context, String providerAlias) {
            redirect(context, providerAlias);
        }
    }
}
