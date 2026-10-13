package org.ihtsdo.refsetservice.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dasniko.testcontainers.keycloak.KeycloakContainer;

/**
 * Exercises {@link EntraIDSecurityServiceHandler} against a real OIDC provider (Keycloak Testcontainers).
 *
 * <p>
 * What this proves beyond {@link EntraIDSecurityServiceHandlerStubJwksTest}: tokens, JWKS, issuer, and audience come from an IdP rather than a hand-minted JWT.
 * </p>
 */
@Testcontainers(disabledWithoutDocker = true)
public class EntraIDSecurityServiceHandlerKeycloakTest {

    /** Shared Keycloak with the MT2 Entra stand-in realm. */
    @Container
    private static final KeycloakContainer KEYCLOAK = KeycloakEntraTestSupport.createContainer();

    /**
     * A Keycloak id token is accepted when JWKS, issuer, and audience match handler config.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenAcceptsKeycloakIdToken() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String idToken =
            KeycloakEntraTestSupport.obtainIdToken(KEYCLOAK, KeycloakEntraTestSupport.MAPPER_USERNAME, KeycloakEntraTestSupport.MAPPER_PASSWORD);

        final User user = handler.authenticateWithBearerToken(idToken);

        assertEquals(KeycloakEntraTestSupport.MAPPER_EMAIL, user.getUserName());
        assertEquals(KeycloakEntraTestSupport.MAPPER_EMAIL, user.getEmail());
        assertEquals("Map Per", user.getName());
        assertTrue(user.getRoles().isEmpty());
    }

    /**
     * Bootstrap admin list matches the Keycloak user's email.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenAppliesBootstrapAdminFromKeycloakEmail() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String idToken =
            KeycloakEntraTestSupport.obtainIdToken(KEYCLOAK, KeycloakEntraTestSupport.ADMIN_USERNAME, KeycloakEntraTestSupport.ADMIN_PASSWORD);

        final User user = handler.authenticateWithBearerToken(idToken);

        assertEquals(KeycloakEntraTestSupport.ADMIN_EMAIL, user.getUserName());
        assertTrue(user.getRoles().contains(EntraMapBootstrap.ROLE_ADMIN));
    }

    /**
     * Keycloak access tokens use a different audience than the app client id, so the Entra handler must reject them.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenRejectsKeycloakAccessTokenAudience() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String accessToken =
            KeycloakEntraTestSupport.obtainAccessToken(KEYCLOAK, KeycloakEntraTestSupport.MAPPER_USERNAME, KeycloakEntraTestSupport.MAPPER_PASSWORD);

        final RestException ex = assertThrows(RestException.class, () -> handler.authenticateWithBearerToken(accessToken));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * Authorize URL is built from Keycloak's authorization endpoint.
     *
     * @throws Exception the exception
     */
    @Test
    public void testBuildBrowserLoginUrlUsesKeycloakAuthorizeEndpoint() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String url = handler.buildBrowserLoginUrl("oauth-state");

        assertTrue(url.startsWith(KeycloakEntraTestSupport.authorizationEndpoint(KEYCLOAK) + "?"));
        assertTrue(url.contains("client_id=" + KeycloakEntraTestSupport.CLIENT_ID));
        assertTrue(url.contains("response_type=code"));
    }

    /**
     * Configures a handler against the running Keycloak realm.
     *
     * @return handler
     * @throws Exception the exception
     */
    private EntraIDSecurityServiceHandler configuredHandler() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        handler.setProperties(KeycloakEntraTestSupport.handlerProperties(KEYCLOAK));
        return handler;
    }
}
