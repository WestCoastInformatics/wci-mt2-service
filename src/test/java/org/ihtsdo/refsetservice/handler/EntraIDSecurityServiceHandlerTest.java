package org.ihtsdo.refsetservice.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.ihtsdo.refsetservice.model.BrowserLoginCallback;
import org.ihtsdo.refsetservice.model.BrowserLoginException;
import org.ihtsdo.refsetservice.model.RestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Basic unit tests for EntraIDSecurityServiceHandler configuration behavior.
 *
 * These tests focus on handler wiring, browser-login guards, and default user list parsing. Token crypto is covered by
 * {@link EntraIDSecurityServiceHandlerStubJwksTest} and Keycloak Testcontainers.
 */
public class EntraIDSecurityServiceHandlerTest {

    /**
     * Clears request attributes left by authenticate() tests.
     */
    @AfterEach
    public void resetRequestAttributes() {

        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Test set properties and system user lists.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSetPropertiesAndSystemUserLists() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("users.admin", "admin1,admin2");
        properties.setProperty("users.spec", "spec1");
        properties.setProperty("users.lead", "lead1; lead2");

        handler.setProperties(properties);

        final Set<String> admins = handler.getSystemAdminUserNames();
        final Set<String> specs = handler.getSystemAuthorUserNames();
        final Set<String> leads = handler.getSystemReviewerUserNames();

        assertEquals(2, admins.size());
        assertEquals(1, specs.size());
        assertEquals(2, leads.size());
        assertTrue(specs.contains("spec1"));
        assertTrue(leads.contains("lead1"));
        assertTrue(leads.contains("lead2"));
    }

    /**
     * Test name and urls with minimal properties.
     *
     * @throws Exception the exception
     */
    @Test
    public void testNameAndUrlsWithMinimalProperties() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("authority", "https://login.microsoftonline.com/tenant");
        properties.setProperty("authorization.endpoint", "https://login.microsoftonline.com/tenant/oauth2/v2.0/authorize");
        properties.setProperty("logout.endpoint", "https://login.microsoftonline.com/tenant/oauth2/v2.0/logout");
        // Explicit none so APP_URL_ROOT from the environment cannot leak into this unit test.
        properties.setProperty("post.logout.redirect.uri", "none");
        handler.setProperties(properties);

        assertEquals("EntraID Security Service handler", handler.getName());
        assertNotNull(handler.getAuthenticateUrl());
        assertNotNull(handler.getLogoutUrl());
        assertEquals("https://login.microsoftonline.com/tenant/oauth2/v2.0/logout", handler.getLogoutUrl());
    }

    /**
     * Test get logout url appends post logout redirect uri.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetLogoutUrlAppendsPostLogoutRedirectUri() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("logout.endpoint", "https://login.microsoftonline.com/tenant/oauth2/v2.0/logout");
        properties.setProperty("post.logout.redirect.uri", "http://localhost:8888/");
        properties.setProperty("client.id", "client-guid");
        handler.setProperties(properties);

        final String url = handler.getLogoutUrl();
        assertEquals(
            "https://login.microsoftonline.com/tenant/oauth2/v2.0/logout?post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A8888%2F&client_id=client-guid", url);
    }

    /**
     * Test get logout url omits post logout when none.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetLogoutUrlOmitsPostLogoutWhenNone() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("logout.endpoint", "https://login.microsoftonline.com/tenant/oauth2/v2.0/logout");
        properties.setProperty("post.logout.redirect.uri", "none");
        handler.setProperties(properties);

        assertEquals("https://login.microsoftonline.com/tenant/oauth2/v2.0/logout", handler.getLogoutUrl());
    }

    /**
     * Test EntraMapBootstrap list split used by role list parsing.
     */
    @Test
    public void testEntraMapBootstrapListSplit() {

        final List<String> mixed = EntraMapBootstrap.splitConfiguredList("a@x.com; b@y.com,c@z.com");
        assertEquals(3, mixed.size());
        assertTrue(mixed.contains("a@x.com"));
        assertTrue(mixed.contains("b@y.com"));
        assertTrue(mixed.contains("c@z.com"));
        assertEquals("all-all-all-admin", EntraMapBootstrap.ROLE_ADMIN);
        assertEquals("all-all-all-lead", EntraMapBootstrap.ROLE_LEAD);
        assertEquals("all-all-all-specialist", EntraMapBootstrap.ROLE_SPEC);
    }

    /**
     * Timeout is owned by SecurityService, not the Entra handler.
     */
    @Test
    public void testTimeoutUserAlwaysFalse() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        assertFalse(handler.timeoutUser("anyone"));
    }

    /**
     * Compute token returns the username unchanged.
     */
    @Test
    public void testComputeTokenForUserReturnsUser() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        assertEquals("mapper@example.test", handler.computeTokenForUser("mapper@example.test"));
    }

    /**
     * Browser login requires CSRF state and supports the callback.
     */
    @Test
    public void testBrowserLoginFlags() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        assertTrue(handler.requiresBrowserLoginState());
        assertTrue(handler.supportsBrowserCallback());
    }

    /**
     * Authorize URL is built from handler properties.
     *
     * @throws Exception the exception
     */
    @Test
    public void testBuildBrowserLoginUrl() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("authorization.endpoint", "https://login.example/tenant/oauth2/v2.0/authorize");
        properties.setProperty("client.id", "client-guid");
        properties.setProperty("redirect.uri", "http://localhost:8888/callback");
        properties.setProperty("scopes", "openid profile email");
        handler.setProperties(properties);

        final String url = handler.buildBrowserLoginUrl("csrf-state");
        assertTrue(url.startsWith("https://login.example/tenant/oauth2/v2.0/authorize?"));
        assertTrue(url.contains("client_id=client-guid"));
        assertTrue(url.contains("state=csrf-state"));
        assertTrue(url.contains("response_type=code"));
    }

    /**
     * Missing authorize pieces fail fast.
     */
    @Test
    public void testBuildBrowserLoginUrlRejectsIncompleteConfig() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        handler.setProperties(new Properties());
        assertThrows(IllegalStateException.class, () -> handler.buildBrowserLoginUrl("state"));
    }

    /**
     * Post-login redirect strips a trailing slash.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetPostLoginRedirectUriStripsTrailingSlash() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("post.login.redirect.uri", "http://localhost:8888/");
        handler.setProperties(properties);
        assertEquals("http://localhost:8888", handler.getPostLoginRedirectUri());
    }

    /**
     * Post-login redirect returns the configured value without a trailing slash.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetPostLoginRedirectUriReturnsConfiguredValue() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("post.login.redirect.uri", "http://localhost:8888/app");
        handler.setProperties(properties);
        assertEquals("http://localhost:8888/app", handler.getPostLoginRedirectUri());
    }

    /**
     * Authenticate without a request context is 401.
     */
    @Test
    public void testAuthenticateRejectsMissingRequest() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final RestException ex = assertThrows(RestException.class, () -> handler.authenticate("ignored"));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * Authenticate without a Bearer header is 401.
     */
    @Test
    public void testAuthenticateRejectsMissingBearer() {

        final MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final RestException ex = assertThrows(RestException.class, () -> handler.authenticate("ignored"));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * Null callback is missing_code.
     */
    @Test
    public void testCompleteBrowserLoginRejectsNullCallback() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final BrowserLoginException ex = assertThrows(BrowserLoginException.class, () -> handler.completeBrowserLogin(null));
        assertEquals("missing_code", ex.getErrorCode());
    }

    /**
     * IdP error is passed through.
     */
    @Test
    public void testCompleteBrowserLoginRejectsIdpError() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final BrowserLoginException ex = assertThrows(BrowserLoginException.class,
            () -> handler.completeBrowserLogin(new BrowserLoginCallback(null, "access_denied", "user cancelled", "s", "s")));
        assertEquals("access_denied", ex.getErrorCode());
    }

    /**
     * Missing code is missing_code.
     */
    @Test
    public void testCompleteBrowserLoginRejectsMissingCode() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final BrowserLoginException ex =
            assertThrows(BrowserLoginException.class, () -> handler.completeBrowserLogin(new BrowserLoginCallback("  ", null, null, "s", "s")));
        assertEquals("missing_code", ex.getErrorCode());
    }

    /**
     * State mismatch is rejected.
     */
    @Test
    public void testCompleteBrowserLoginRejectsStateMismatch() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final BrowserLoginException ex = assertThrows(BrowserLoginException.class,
            () -> handler.completeBrowserLogin(new BrowserLoginCallback("code", null, null, "callback-state", "session-state")));
        assertEquals("state_mismatch", ex.getErrorCode());
    }

    /**
     * Missing token endpoint / secret is config.
     */
    @Test
    public void testCompleteBrowserLoginRejectsIncompleteConfig() {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        handler.setProperties(new Properties());
        final BrowserLoginException ex =
            assertThrows(BrowserLoginException.class, () -> handler.completeBrowserLogin(new BrowserLoginCallback("code", null, null, "s", "s")));
        assertEquals("config", ex.getErrorCode());
    }

    /**
     * Authenticate URL falls back to authority when authorization.endpoint is unset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetAuthenticateUrlFallsBackToAuthority() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        final Properties properties = new Properties();
        properties.setProperty("authority", "https://login.example/tenant");
        handler.setProperties(properties);
        assertEquals("https://login.example/tenant", handler.getAuthenticateUrl());
    }

    /**
     * Null properties yield null authenticate URL.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetAuthenticateUrlNullProperties() throws Exception {

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        assertNull(handler.getAuthenticateUrl());
    }
}
