package io.skycloak.keycloak.idpdiscovery;

import java.util.List;
import java.util.stream.Stream;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.authentication.authenticators.browser.IdentityProviderAuthenticator;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.authentication.authenticators.resetcred.ResetCredentialChooseUser;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

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
        clearUserIfComingFromResetPassword(context);

        if (context.getUser() != null) {
            // A pre-set context user here almost always means we're continuing right
            // after that same user brokered in via an IdP (post-broker-login, or a
            // step-up re-auth in the same session). Redirecting straight back to it
            // would loop. Stock UsernameForm.authenticate() guards this exact case by
            // filtering the just-used IdP (BROKERED_CONTEXT_NOTE) out of the user's
            // linked-broker set before deciding whether to short-circuit; mirror that
            // here before picking a redirect target.
            redirectOrContinue(context, false, brokeredIdpAlias(context));
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
        // (and therefore UsernameForm) in Keycloak 26.7.3.
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

        redirectOrContinue(context, true, null);
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

    /**
     * A user picked on the unauthenticated "forgot password" screen must not be
     * inherited by the login step as an already-resolved identity. Keycloak 26.7.x
     * added this same clear at the top of {@code UsernamePasswordForm.authenticate},
     * where it is private; because this class short-circuits on a pre-set user
     * before delegating to {@code super}, that guard would otherwise never run on
     * the branch that skips the username form.
     */
    private static void clearUserIfComingFromResetPassword(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        if (!isResetPasswordUser(authSession.getAuthNote(ResetCredentialChooseUser.RESET_CREDENTIAL_USER_CHOSEN))) {
            return;
        }
        context.clearUser();
        authSession.removeAuthNote(ResetCredentialChooseUser.RESET_CREDENTIAL_USER_CHOSEN);
    }

    /** Split out from {@link #clearUserIfComingFromResetPassword} so it is unit-testable. */
    static boolean isResetPasswordUser(String authNoteValue) {
        return "true".equals(authNoteValue);
    }

    private void redirectOrContinue(AuthenticationFlowContext context, boolean resolvedFromForm, String excludedIdpAlias) {
        LinkedIdentityProviderSelection selection = linkedIdentityProviderSelection(
                context.getSession(), context.getRealm(), context.getUser(), excludedIdpAlias);
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

    /**
     * Selects a redirect target from a user's linked identity providers, excluding
     * {@code excludedIdpAlias} (the IdP that just brokered this user in, if any) so
     * the re-authentication path can't loop back to where it came from. Mirrors
     * {@code UsernameForm.hasLinkedBrokers()}'s filtering, adapted to also pick the
     * one remaining candidate when exactly one survives the filter.
     */
    static LinkedIdentityProviderSelection selectLinkedIdentityProvider(
            Stream<FederatedIdentityModel> identities, String excludedIdpAlias) {
        List<FederatedIdentityModel> candidates = identities
                .filter(identity -> excludedIdpAlias == null || !excludedIdpAlias.equals(identity.getIdentityProvider()))
                .limit(2)
                .toList();
        if (candidates.size() == 1) {
            return new LinkedIdentityProviderSelection(candidates.get(0).getIdentityProvider(), false);
        }
        return new LinkedIdentityProviderSelection(null, candidates.size() > 1);
    }

    private static LinkedIdentityProviderSelection linkedIdentityProviderSelection(
            KeycloakSession session, RealmModel realm, UserModel user, String excludedIdpAlias) {
        try (Stream<FederatedIdentityModel> identities = session.users()
                .getFederatedIdentitiesStream(realm, user)) {
            return selectLinkedIdentityProvider(identities, excludedIdpAlias);
        }
    }

    /**
     * The alias of the identity provider that brokered the current auth session's
     * user in, if any. Read from the same {@code BROKERED_CONTEXT_NOTE} that stock
     * {@code UsernameForm} checks, via the same {@link SerializedBrokeredIdentityContext}
     * deserialization path.
     */
    private static String brokeredIdpAlias(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        SerializedBrokeredIdentityContext serializedCtx = SerializedBrokeredIdentityContext
                .readFromAuthenticationSession(authSession, AbstractIdpAuthenticator.BROKERED_CONTEXT_NOTE);
        if (serializedCtx == null) {
            return null;
        }
        IdentityProviderModel idpConfig = serializedCtx.deserialize(context.getSession(), authSession).getIdpConfig();
        return idpConfig == null ? null : idpConfig.getAlias();
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
