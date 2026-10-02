package org.ihtsdo.refsetservice.handler;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Properties;

import org.ihtsdo.refsetservice.util.ThreadLocalMapper;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.databind.JsonNode;

import dasniko.testcontainers.keycloak.KeycloakContainer;

/**
 * Shared Keycloak Testcontainers fixture used as an OIDC stand-in for Entra ID tests.
 */
public final class KeycloakEntraTestSupport {

    /** Realm imported into the container. */
    public static final String REALM = "mt2-entra-test";

    /** Confidential client id (JWT audience for id tokens). */
    public static final String CLIENT_ID = "mt2-service";

    /** Confidential client secret. */
    public static final String CLIENT_SECRET = "test-client-secret";

    /** Registered redirect URI for the authorization code flow. */
    public static final String REDIRECT_URI = "http://127.0.0.1:8888/refsetservice/authenticate/callback";

    /** Specialist test user username. */
    public static final String MAPPER_USERNAME = "mapper";

    /** Specialist test user email (default identifier claim). */
    public static final String MAPPER_EMAIL = "mapper@example.test";

    /** Specialist test user password. */
    public static final String MAPPER_PASSWORD = "mapper-pass";

    /** Admin test user username. */
    public static final String ADMIN_USERNAME = "admin.mapper";

    /** Admin test user email. */
    public static final String ADMIN_EMAIL = "admin.mapper@example.test";

    /** Admin test user password. */
    public static final String ADMIN_PASSWORD = "admin-pass";

    /** Keycloak image aligned with dasniko 3.6.0. */
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.1";

    /**
     * Instantiates a new keycloak entra test support.
     */
    private KeycloakEntraTestSupport() {

    }

    /**
     * Creates a Keycloak container that imports the MT2 Entra stand-in realm.
     *
     * @return unstarted container
     */
    public static KeycloakContainer createContainer() {

        return new KeycloakContainer(KEYCLOAK_IMAGE).withRealmImportFile("keycloak/mt2-entra-test-realm.json").withRamPercentage(25, 70)
            .withStartupTimeout(Duration.ofMinutes(4));
    }

    /**
     * Returns the realm issuer (JWT {@code iss}).
     *
     * @param keycloak the container
     * @return issuer URL
     */
    public static String issuer(final KeycloakContainer keycloak) {

        return keycloak.getAuthServerUrl() + "/realms/" + REALM;
    }

    /**
     * Returns the JWKS URL for the imported realm.
     *
     * @param keycloak the container
     * @return JWKS URL
     */
    public static String jwksEndpoint(final KeycloakContainer keycloak) {

        return issuer(keycloak) + "/protocol/openid-connect/certs";
    }

    /**
     * Returns the token endpoint for the imported realm.
     *
     * @param keycloak the container
     * @return token URL
     */
    public static String tokenEndpoint(final KeycloakContainer keycloak) {

        return issuer(keycloak) + "/protocol/openid-connect/token";
    }

    /**
     * Returns the authorization endpoint for the imported realm.
     *
     * @param keycloak the container
     * @return authorize URL
     */
    public static String authorizationEndpoint(final KeycloakContainer keycloak) {

        return issuer(keycloak) + "/protocol/openid-connect/auth";
    }

    /**
     * Returns the logout endpoint for the imported realm.
     *
     * @param keycloak the container
     * @return logout URL
     */
    public static String logoutEndpoint(final KeycloakContainer keycloak) {

        return issuer(keycloak) + "/protocol/openid-connect/logout";
    }

    /**
     * Builds Entra handler properties pointed at this Keycloak realm.
     *
     * @param keycloak the container
     * @return handler properties
     */
    public static Properties handlerProperties(final KeycloakContainer keycloak) {

        final Properties properties = new Properties();
        properties.setProperty("jwks.endpoint", jwksEndpoint(keycloak));
        properties.setProperty("authority", issuer(keycloak));
        properties.setProperty("client.id", CLIENT_ID);
        properties.setProperty("client.secret", CLIENT_SECRET);
        properties.setProperty("token.endpoint", tokenEndpoint(keycloak));
        properties.setProperty("authorization.endpoint", authorizationEndpoint(keycloak));
        properties.setProperty("logout.endpoint", logoutEndpoint(keycloak));
        properties.setProperty("redirect.uri", REDIRECT_URI);
        properties.setProperty("scopes", "openid profile email");
        properties.setProperty("users.admin", ADMIN_EMAIL);
        return properties;
    }

    /**
     * Obtains an id token via the resource-owner password grant.
     *
     * @param keycloak the container
     * @param username the username
     * @param password the password
     * @return id token JWT
     * @throws Exception if the token request fails
     */
    public static String obtainIdToken(final KeycloakContainer keycloak, final String username, final String password) throws Exception {

        return waitForNotBefore(requireTokenField(requestPasswordGrant(keycloak, username, password), "id_token"));
    }

    /**
     * Obtains an access token via the resource-owner password grant.
     *
     * @param keycloak the container
     * @param username the username
     * @param password the password
     * @return access token JWT
     * @throws Exception if the token request fails
     */
    public static String obtainAccessToken(final KeycloakContainer keycloak, final String username, final String password) throws Exception {

        return waitForNotBefore(requireTokenField(requestPasswordGrant(keycloak, username, password), "access_token"));
    }

    /**
     * Requests a password-grant token response.
     *
     * @param keycloak the container
     * @param username the username
     * @param password the password
     * @return parsed JSON body
     * @throws Exception if the HTTP call fails
     */
    private static JsonNode requestPasswordGrant(final KeycloakContainer keycloak, final String username, final String password) throws Exception {

        final String form =
            String.join("&", "grant_type=" + urlEncode("password"), "client_id=" + urlEncode(CLIENT_ID), "client_secret=" + urlEncode(CLIENT_SECRET),
                "username=" + urlEncode(username), "password=" + urlEncode(password), "scope=" + urlEncode("openid email profile"));
        final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint(keycloak))).timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Keycloak password grant HTTP " + response.statusCode() + " body=" + response.body());
        }
        return ThreadLocalMapper.get().readTree(response.body());
    }

    /**
     * Waits until the token {@code nbf} claim is in the past (Docker clock can be slightly ahead of the host).
     *
     * @param token JWT
     * @return the same token
     * @throws InterruptedException if the wait is interrupted
     */
    private static String waitForNotBefore(final String token) throws InterruptedException {

        final Date nbf = JWT.decode(token).getNotBefore();
        if (nbf == null) {
            return token;
        }
        final long delayMs = nbf.getTime() - System.currentTimeMillis() + 50L;
        if (delayMs > 0 && delayMs < 5_000L) {
            Thread.sleep(delayMs);
        }
        return token;
    }

    /**
     * Reads a required token field.
     *
     * @param root token JSON
     * @param field field name
     * @return field value
     */
    private static String requireTokenField(final JsonNode root, final String field) {

        final JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalStateException("Keycloak token response missing " + field);
        }
        return node.asText();
    }

    /**
     * URL-encodes a form value.
     *
     * @param value raw value
     * @return encoded value
     */
    private static String urlEncode(final String value) {

        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
