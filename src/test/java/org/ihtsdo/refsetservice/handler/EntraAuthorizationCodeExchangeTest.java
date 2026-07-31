package org.ihtsdo.refsetservice.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.ihtsdo.refsetservice.util.EntraAuthorizationCodeExchange;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Exercises {@link EntraAuthorizationCodeExchange} against a local HTTP server that returns Entra-shaped JSON. Proves form POST, status handling, and
 * {@code id_token} vs {@code access_token} selection without calling Microsoft.
 */
public class EntraAuthorizationCodeExchangeTest {

    /**
     * When both are present, {@code id_token} wins (matches Entra handler preference for app audience).
     *
     * @throws Exception the exception
     */
    @Test
    public void testExchangeCodeForJwtPrefersIdTokenWhenBothPresent() throws Exception {

        final HttpServer server = startTokenServer(200, "{\"id_token\":\"jwt-id\",\"access_token\":\"jwt-access\"}");
        try {
            final int port = server.getAddress().getPort();
            final String url = "http://127.0.0.1:" + port + "/token";
            final String jwt = EntraAuthorizationCodeExchange.exchangeCodeForJwt(url, "client-id", "secret", "auth-code", "https://app/callback");
            assertEquals("jwt-id", jwt);
        } finally {
            server.stop(0);
        }
    }

    /**
     * Falls back to {@code access_token} when {@code id_token} is absent.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExchangeCodeForJwtUsesAccessTokenWhenNoIdToken() throws Exception {

        final HttpServer server = startTokenServer(200, "{\"access_token\":\"jwt-access-only\"}");
        try {
            final int port = server.getAddress().getPort();
            final String url = "http://127.0.0.1:" + port + "/token";
            final String jwt = EntraAuthorizationCodeExchange.exchangeCodeForJwt(url, "c", "s", "code", "https://r");
            assertEquals("jwt-access-only", jwt);
        } finally {
            server.stop(0);
        }
    }

    /**
     * Non-2xx HTTP status yields {@link IllegalStateException}.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExchangeCodeForJwtRejectsNon2xxHttp() throws Exception {

        final HttpServer server = startTokenServer(400, "{\"error\":\"invalid_grant\"}");
        try {
            final int port = server.getAddress().getPort();
            final String url = "http://127.0.0.1:" + port + "/token";
            assertThrows(IllegalStateException.class, () -> EntraAuthorizationCodeExchange.exchangeCodeForJwt(url, "c", "s", "code", "https://r"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 2xx body with {@code error} field yields {@link IllegalStateException} (Entra error-in-body pattern).
     *
     * @throws Exception the exception
     */
    @Test
    public void testExchangeCodeForJwtRejectsJsonErrorField() throws Exception {

        final HttpServer server = startTokenServer(200, "{\"error\":\"invalid_client\",\"error_description\":\"bad\"}");
        try {
            final int port = server.getAddress().getPort();
            final String url = "http://127.0.0.1:" + port + "/token";
            assertThrows(IllegalStateException.class, () -> EntraAuthorizationCodeExchange.exchangeCodeForJwt(url, "c", "s", "code", "https://r"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * Authorize URL shape: required query parameters and UTF-8 encoding (spaces in scope, reserved chars in state).
     */
    @Test
    public void testBuildAuthorizeUrlEncodesQueryParameters() {

        final String url = EntraAuthorizationCodeExchange.buildAuthorizeUrl("https://login.example/tenant/oauth2/v2.0/authorize", "client-1",
            "https://app/refsetservice/authenticate/callback", "openid profile email", "state-with+chars");

        assertTrue(url.startsWith("https://login.example/tenant/oauth2/v2.0/authorize?"));
        assertTrue(url.contains("client_id="));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("scope="));
        assertTrue(url.contains("state="));
        assertTrue(url.contains("response_mode=query"));
        assertTrue(url.contains("openid"), "scope should be present (encoded)");
    }

    /**
     * Builds the logout url appends post logout redirect uri.
     */
    @Test
    public void testBuildLogoutUrlAppendsPostLogoutRedirectUri() {

        final String url = EntraAuthorizationCodeExchange.buildLogoutUrl("https://login.example/tenant/oauth2/v2.0/logout", "http://localhost:8888/",
            "client-1");
        assertEquals(
            "https://login.example/tenant/oauth2/v2.0/logout?post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A8888%2F&client_id=client-1", url);
    }

    /**
     * Builds the logout url omits when blank or none.
     */
    @Test
    public void testBuildLogoutUrlOmitsWhenBlankOrNone() {

        assertEquals("https://login.example/logout", EntraAuthorizationCodeExchange.buildLogoutUrl("https://login.example/logout", null));
        assertEquals("https://login.example/logout", EntraAuthorizationCodeExchange.buildLogoutUrl("https://login.example/logout", "none"));
        assertEquals("https://login.example/logout", EntraAuthorizationCodeExchange.buildLogoutUrl("https://login.example/logout", "  "));
    }

    /**
     * Builds the logout url rejects blank endpoint.
     */
    @Test
    public void testBuildLogoutUrlRejectsBlankEndpoint() {

        assertThrows(IllegalArgumentException.class, () -> EntraAuthorizationCodeExchange.buildLogoutUrl("  ", "http://localhost/"));
    }

    /**
     * Start token server.
     *
     * @param status the status
     * @param jsonBody the json body
     * @return the http server
     * @throws Exception the exception
     */
    private static HttpServer startTokenServer(final int status, final String jsonBody) throws Exception {

        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {

            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            try (InputStream in = ex.getRequestBody()) {
                in.readAllBytes();
            }
            final byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }
}
