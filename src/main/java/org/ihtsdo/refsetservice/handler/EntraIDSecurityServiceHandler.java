/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * Implements a security handler that authorizes via Microsoft EntraID for Norway deployments.
 */
public class EntraIDSecurityServiceHandler implements SecurityServiceHandler {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(EntraIDSecurityServiceHandler.class);

    /** The handler-scoped properties (security.handler.ENTRAID.*). */
    private Properties properties;

    /* see superclass */
    @Override
    public User authenticate(final String userName) throws Exception {

        final String token = extractBearerTokenFromRequest();

        if (StringUtils.isBlank(token)) {
            throw new RestException(false, 401, "Unauthorized", "Missing or invalid Authorization bearer token.");
        }

        final String jwksEndpoint = getRequiredProperty("jwks.endpoint");
        final String authority = getRequiredProperty("authority");
        final String clientId = getRequiredProperty("client.id");

        try {
            final DecodedJWT decodedJwt = validateToken(token, jwksEndpoint, authority, clientId);

            final String identifierClaim = properties != null && StringUtils.isNotBlank(properties.getProperty("user.identifier.claim"))
                ? properties.getProperty("user.identifier.claim")
                : "email";

            final String identifier = decodedJwt.getClaim(identifierClaim).asString();
            if (StringUtils.isBlank(identifier)) {
                throw new RestException(false, 401, "Unauthorized", "Token missing required claim: " + identifierClaim);
            }

            final String name = decodedJwt.getClaim("name").asString();

            final User user = new User();
            user.setUserName(identifier);
            user.setEmail(identifier);
            user.setName(StringUtils.isNotBlank(name) ? name : identifier);
            user.setRoles(new HashSet<>());

            applyDefaultRoles(user);

            user.setModifiedBy(user.getUserName());

            LOG.debug("EntraID authenticate user is: {}", user);
            return user;

        } catch (final RestException e) {
            throw e;
        } catch (final Exception e) {
            LOG.error("Error validating EntraID token", e);
            throw new RestException(false, 401, "Unauthorized", "Unable to validate EntraID token.");
        }
    }

    /**
     * Extracts the bearer token from the current HTTP request.
     *
     * @return the bearer token or null
     */
    private String extractBearerTokenFromRequest() {

        final ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {
            return null;
        }

        final String authHeader = requestAttributes.getRequest().getHeader("Authorization");
        if (StringUtils.isBlank(authHeader)) {
            return null;
        }

        final String lower = authHeader.toLowerCase();
        if (!lower.startsWith("bearer ")) {
            return null;
        }

        return authHeader.substring(7).trim();
    }

    /**
     * Validates the JWT token using the configured JWKS endpoint, issuer and audience.
     *
     * @param token the token
     * @param jwksEndpoint the jwks endpoint
     * @param authority the authority (issuer)
     * @param clientId the client id (audience)
     * @return the decoded jwt
     * @throws Exception the exception
     */
    private DecodedJWT validateToken(final String token, final String jwksEndpoint, final String authority, final String clientId) throws Exception {

        final DecodedJWT jwt = JWT.decode(token);

        final JwkProvider provider = new UrlJwkProvider(new URL(jwksEndpoint));
        final Jwk jwk = provider.get(jwt.getKeyId());
        final Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

        final JWTVerifier verifier = JWT.require(algorithm)
            .withIssuer(authority)
            .withAudience(clientId)
            .build();

        return verifier.verify(token);
    }

    /**
     * Applies default roles from configuration for first-time or property-based users.
     *
     * @param user the user
     */
    private void applyDefaultRoles(final User user) {

        if (properties == null) {
            return;
        }

        final String userName = user.getUserName();
        if (StringUtils.isBlank(userName)) {
            return;
        }

        final Set<String> rolesToAdd = new HashSet<>();

        addRolesIfConfigured(userName, "users.admin", "all-all-all-admin", rolesToAdd);
        addRolesIfConfigured(userName, "users.author", "all-all-all-author", rolesToAdd);
        addRolesIfConfigured(userName, "users.reviewer", "all-all-all-reviewer", rolesToAdd);

        if (!rolesToAdd.isEmpty()) {
            user.getRoles().addAll(rolesToAdd);
        }
    }

    /**
     * Adds a role if the user is configured under the specified key.
     *
     * @param userName the user name
     * @param key the key
     * @param role the role
     * @param rolesToAdd the roles to add
     */
    private void addRolesIfConfigured(final String userName, final String key, final String role, final Set<String> rolesToAdd) {

        final String list = properties.getProperty(key);
        if (StringUtils.isBlank(list)) {
            return;
        }

        final String[] users = list.split(",");
        for (final String entry : users) {
            if (userName.equalsIgnoreCase(entry.trim())) {
                rolesToAdd.add(role);
                break;
            }
        }
    }

    /**
     * Returns a required property from handler properties.
     *
     * @param key the key
     * @return the required property
     * @throws RestException if missing
     */
    private String getRequiredProperty(final String key) throws RestException {

        if (properties == null) {
            throw new RestException(false, 500, "Server Error",
                "EntraID handler properties not configured for key: " + key);
        }
        final String value = properties.getProperty(key);
        if (StringUtils.isBlank(value)) {
            throw new RestException(false, 500, "Server Error",
                "Missing required EntraID handler property: " + key);
        }
        return value;
    }

    /* see superclass */
    @Override
    public boolean timeoutUser(final String user) {

        // Never timeout user here, SecurityService manages token timeout.
        return false;
    }

    /* see superclass */
    @Override
    public String computeTokenForUser(final String user) {

        return user;
    }

    /* see superclass */
    @Override
    public void setProperties(final Properties properties) {

        this.properties = properties;
    }

    /* see superclass */
    @Override
    public String getName() {

        return "EntraID Security Service handler";
    }

    /* see superclass */
    @Override
    public String getAuthenticateUrl() throws Exception {

        if (properties == null) {
            return null;
        }
        final String endpoint = properties.getProperty("authorization.endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            return endpoint;
        }
        return properties.getProperty("authority");
    }

    /* see superclass */
    @Override
    public String getLogoutUrl() throws Exception {

        if (properties == null) {
            return null;
        }
        final String endpoint = properties.getProperty("logout.endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            return endpoint;
        }
        return null;
    }

    /* see superclass */
    @Override
    public Set<String> getSystemAdminUserNames() throws Exception {

        if (properties == null || StringUtils.isBlank(properties.getProperty("users.admin"))) {
            return new HashSet<>();
        }
        final String propertyValue = properties.getProperty("users.admin").trim();
        if (propertyValue.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(propertyValue.split("\\s*,\\s*")).collect(Collectors.toSet());
    }

    /* see superclass */
    @Override
    public Set<String> getSystemAuthorUserNames() throws Exception {

        if (properties == null || StringUtils.isBlank(properties.getProperty("users.author"))) {
            return new HashSet<>();
        }
        final String propertyValue = properties.getProperty("users.author").trim();
        if (propertyValue.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(propertyValue.split("\\s*,\\s*")).collect(Collectors.toSet());
    }

    /* see superclass */
    @Override
    public Set<String> getSystemReviewerUserNames() throws Exception {

        if (properties == null || StringUtils.isBlank(properties.getProperty("users.reviewer"))) {
            return new HashSet<>();
        }
        final String propertyValue = properties.getProperty("users.reviewer").trim();
        if (propertyValue.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(propertyValue.split("\\s*,\\s*")).collect(Collectors.toSet());
    }
}

