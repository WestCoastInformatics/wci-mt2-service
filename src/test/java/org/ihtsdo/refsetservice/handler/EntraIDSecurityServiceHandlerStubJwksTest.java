package org.ihtsdo.refsetservice.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

import org.ihtsdo.refsetservice.model.BrowserLoginCallback;
import org.ihtsdo.refsetservice.model.RestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sun.net.httpserver.HttpServer;

/**
 * Offline substitute for the customer's Entra tenant. What this gives you confidence in: JWKS fetch, RSA signature verification, issuer/audience/expiries,
 * {@code kid} resolution, and required identifier claim handling — the same code paths {@link EntraIDSecurityServiceHandler} uses in production. What it cannot
 * prove: Conditional Access, browser redirects, or Microsoft's token endpoint behavior (see {@link EntraAuthorizationCodeExchangeTest} for the exchange client
 * against a stub HTTP server).
 */
public class EntraIDSecurityServiceHandlerStubJwksTest {

    /** The Constant KID. */
    private static final String KID = "stub-entra-test-key";

    /** The http server. */
    private HttpServer httpServer;

    /** The jwks base url. */
    private String jwksBaseUrl;

    /** The key pair. */
    private KeyPair keyPair;

    /** The test issuer. */
    private String testIssuer;

    /** The test client id. */
    private String testClientId;

    /**
     * Starts an HTTP server that serves a minimal JWKS document for {@link #keyPair}.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void startJwksServer() throws Exception {

        final KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        testIssuer = "https://stub-entra.test/" + UUID.randomUUID();
        testClientId = "stub-client-" + UUID.randomUUID();

        final RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
        final String n = base64UrlUnsigned(pub.getModulus());
        final String e = base64UrlUnsigned(BigInteger.valueOf(pub.getPublicExponent().longValue()));

        final String jwksJson =
            "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KID + "\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";

        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/discovery/v2.0/keys", ex -> {

            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            final byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (final OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.start();
        final int port = httpServer.getAddress().getPort();
        jwksBaseUrl = "http://127.0.0.1:" + port + "/discovery/v2.0/keys";
    }

    /**
     * Stops the JWKS server.
     */
    @AfterEach
    public void stopJwksServer() {

        RequestContextHolder.resetRequestAttributes();
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    /**
     * {@link EntraIDSecurityServiceHandler#authenticateWithBearerToken(String)} accepts a JWT whose issuer, audience, signature, and email claim match handler
     * configuration backed by the local JWKS endpoint.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenValidatesAgainstLocalJwks() throws Exception {

        final Properties properties = new Properties();
        properties.setProperty("jwks.endpoint", jwksBaseUrl);
        properties.setProperty("authority", testIssuer);
        properties.setProperty("client.id", testClientId);

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        handler.setProperties(properties);

        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), testIssuer, testClientId, "dev.user@stub-entra.test", "Dev User");

        final org.ihtsdo.refsetservice.model.User user = handler.authenticateWithBearerToken(token);

        assertEquals("dev.user@stub-entra.test", user.getUserName());
        assertEquals("dev.user@stub-entra.test", user.getEmail());
        assertEquals("Dev User", user.getName());
    }

    /**
     * Bootstrap admin list matches token email case-insensitively.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenAppliesBootstrapAdminRole() throws Exception {

        final Properties properties = new Properties();
        properties.setProperty("jwks.endpoint", jwksBaseUrl);
        properties.setProperty("authority", testIssuer);
        properties.setProperty("client.id", testClientId);
        properties.setProperty("users.admin", "admin@stub-entra.test");

        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        handler.setProperties(properties);

        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), testIssuer, testClientId, "admin@stub-entra.test", "Admin");

        final org.ihtsdo.refsetservice.model.User user = handler.authenticateWithBearerToken(token);

        assertTrue(user.getRoles().contains("all-all-all-admin"));
    }

    /**
     * Wrong {@code aud} must not verify (prevents using tokens minted for another app registration).
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenRejectsWrongAudience() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), KID, testIssuer, "other-audience", "u@x.test", "N", new Date(),
            new Date(System.currentTimeMillis() + 3600_000L));

        final RestException ex = assertThrows(RestException.class, () -> handler.authenticateWithBearerToken(token));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * Wrong {@code iss} must not verify (prevents cross-tenant token replay).
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenRejectsWrongIssuer() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), KID, "https://evil-tenant.test/", testClientId, "u@x.test", "N", new Date(),
            new Date(System.currentTimeMillis() + 3600_000L));

        final RestException ex = assertThrows(RestException.class, () -> handler.authenticateWithBearerToken(token));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * Expired tokens must not verify.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenRejectsExpiredToken() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), KID, testIssuer, testClientId, "u@x.test", "N", new Date(),
            new Date(System.currentTimeMillis() - 120_000L));

        final RestException ex = assertThrows(RestException.class, () -> handler.authenticateWithBearerToken(token));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * {@code kid} not present in JWKS must fail key resolution.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenRejectsUnknownKeyId() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), "unknown-kid", testIssuer, testClientId, "u@x.test", "N", new Date(),
            new Date(System.currentTimeMillis() + 3600_000L));

        final RestException ex = assertThrows(RestException.class, () -> handler.authenticateWithBearerToken(token));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * Corrupted signature must not verify.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenRejectsTamperedSignature() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), testIssuer, testClientId, "u@x.test", "N");
        final String[] parts = token.split("\\.");
        final String tampered = parts[0] + "." + parts[1] + ".bm9w";

        final RestException ex = assertThrows(RestException.class, () -> handler.authenticateWithBearerToken(tampered));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * Missing configured identifier claim ({@code email} by default) yields 401 after successful crypto verify.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenRejectsMissingEmailClaim() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final Algorithm algorithm = Algorithm.RSA256(null, (RSAPrivateKey) keyPair.getPrivate());
        final String token = JWT.create().withKeyId(KID).withIssuer(testIssuer).withAudience(testClientId).withSubject(UUID.randomUUID().toString())
            .withIssuedAt(new Date()).withExpiresAt(new Date(System.currentTimeMillis() + 3600_000L)).withClaim("name", "No Email").sign(algorithm);

        final RestException ex = assertThrows(RestException.class, () -> handler.authenticateWithBearerToken(token));
        assertEquals(401, ex.getError().getStatus());
    }

    /**
     * {@link EntraIDSecurityServiceHandler#authenticate(String)} reads the Bearer header from the current request.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateReadsBearerFromRequest() throws Exception {

        final EntraIDSecurityServiceHandler handler = configuredHandler();
        final String token = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), testIssuer, testClientId, "dev.user@stub-entra.test", "Dev User");
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        final org.ihtsdo.refsetservice.model.User user = handler.authenticate("ignored");

        assertEquals("dev.user@stub-entra.test", user.getUserName());
    }

    /**
     * Configured identifier claim can be {@code preferred_username} instead of {@code email}.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAuthenticateWithBearerTokenUsesConfiguredIdentifierClaim() throws Exception {

        final Properties properties = new Properties();
        properties.setProperty("jwks.endpoint", jwksBaseUrl);
        properties.setProperty("authority", testIssuer);
        properties.setProperty("client.id", testClientId);
        properties.setProperty("user.identifier.claim", "preferred_username");
        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        handler.setProperties(properties);

        final Algorithm algorithm = Algorithm.RSA256(null, (RSAPrivateKey) keyPair.getPrivate());
        final String token = JWT.create().withKeyId(KID).withIssuer(testIssuer).withAudience(testClientId).withSubject(UUID.randomUUID().toString())
            .withIssuedAt(new Date()).withExpiresAt(new Date(System.currentTimeMillis() + 3600_000L))
            .withClaim("preferred_username", "pref.user@stub-entra.test").withClaim("name", "Pref User").sign(algorithm);

        final org.ihtsdo.refsetservice.model.User user = handler.authenticateWithBearerToken(token);

        assertEquals("pref.user@stub-entra.test", user.getUserName());
        assertEquals("Pref User", user.getName());
    }

    /**
     * {@link EntraIDSecurityServiceHandler#completeBrowserLogin} exchanges the code then validates the returned JWT.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCompleteBrowserLoginExchangesStubIdToken() throws Exception {

        final String jwt = mintAccessToken((RSAPrivateKey) keyPair.getPrivate(), testIssuer, testClientId, "dev.user@stub-entra.test", "Dev User");
        final HttpServer tokenServer = startJsonPostServer("{\"id_token\":\"" + jwt + "\"}");
        try {
            final Properties properties = new Properties();
            properties.setProperty("jwks.endpoint", jwksBaseUrl);
            properties.setProperty("authority", testIssuer);
            properties.setProperty("client.id", testClientId);
            properties.setProperty("client.secret", "stub-secret");
            properties.setProperty("token.endpoint", "http://127.0.0.1:" + tokenServer.getAddress().getPort() + "/token");
            properties.setProperty("redirect.uri", "http://localhost:8888/callback");
            final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
            handler.setProperties(properties);

            final org.ihtsdo.refsetservice.model.User user =
                handler.completeBrowserLogin(new BrowserLoginCallback("auth-code", null, null, "state-1", "state-1"));

            assertEquals("dev.user@stub-entra.test", user.getUserName());
        } finally {
            tokenServer.stop(0);
        }
    }

    /**
     * Configured handler.
     *
     * @return the entra ID security service handler
     * @throws Exception the exception
     */
    private EntraIDSecurityServiceHandler configuredHandler() throws Exception {

        final Properties properties = new Properties();
        properties.setProperty("jwks.endpoint", jwksBaseUrl);
        properties.setProperty("authority", testIssuer);
        properties.setProperty("client.id", testClientId);
        final EntraIDSecurityServiceHandler handler = new EntraIDSecurityServiceHandler();
        handler.setProperties(properties);
        return handler;
    }

    /**
     * Mint access token.
     *
     * @param privateKey the private key
     * @param issuer the issuer
     * @param audience the audience
     * @param email the email
     * @param name the name
     * @return the string
     */
    private static String mintAccessToken(final RSAPrivateKey privateKey, final String issuer, final String audience, final String email, final String name) {

        return mintAccessToken(privateKey, KID, issuer, audience, email, name, new Date(), new Date(System.currentTimeMillis() + 3600_000L));
    }

    /**
     * Mint access token.
     *
     * @param privateKey the private key
     * @param keyId the key id
     * @param issuer the issuer
     * @param audience the audience
     * @param email the email
     * @param name the name
     * @param issuedAt the issued at
     * @param expiresAt the expires at
     * @return the string
     */
    private static String mintAccessToken(final RSAPrivateKey privateKey, final String keyId, final String issuer, final String audience, final String email,
        final String name, final Date issuedAt, final Date expiresAt) {

        final Algorithm algorithm = Algorithm.RSA256(null, privateKey);
        return JWT.create().withKeyId(keyId).withIssuer(issuer).withAudience(audience).withSubject(UUID.randomUUID().toString()).withIssuedAt(issuedAt)
            .withExpiresAt(expiresAt).withClaim("email", email).withClaim("name", name).sign(algorithm);
    }

    /**
     * Base 64 url unsigned.
     *
     * @param value the value
     * @return the string
     */
    private static String base64UrlUnsigned(final BigInteger value) {

        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Starts a local POST endpoint that returns a JSON body.
     *
     * @param jsonBody response body
     * @return started server
     * @throws Exception the exception
     */
    private static HttpServer startJsonPostServer(final String jsonBody) throws Exception {

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
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }
}
