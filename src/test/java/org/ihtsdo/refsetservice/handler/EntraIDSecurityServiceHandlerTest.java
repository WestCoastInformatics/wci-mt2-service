package org.ihtsdo.refsetservice.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Basic unit tests for EntraIDSecurityServiceHandler configuration behavior.
 *
 * These tests focus on handler wiring and default user list parsing. Full token validation is exercised via integration tests.
 */
public class EntraIDSecurityServiceHandlerTest {

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
        properties.setProperty("users.author", "author1");
        properties.setProperty("users.reviewer", "reviewer1, reviewer2");

        handler.setProperties(properties);

        final Set<String> admins = handler.getSystemAdminUserNames();
        final Set<String> authors = handler.getSystemAuthorUserNames();
        final Set<String> reviewers = handler.getSystemReviewerUserNames();

        assertEquals(2, admins.size());
        assertEquals(1, authors.size());
        assertEquals(2, reviewers.size());
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
            "https://login.microsoftonline.com/tenant/oauth2/v2.0/logout?post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A8888%2F&client_id=client-guid",
            url);
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
}
