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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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

        LOG.info("EntraID authenticate: start (path userName parameter={}, ignored for identity; identity comes from JWT claims)", userName);

        final String token = extractBearerTokenFromRequest();

        if (StringUtils.isBlank(token)) {
            LOG.warn("EntraID authenticate: abort — no bearer token on request (Authorization missing, blank, or not Bearer scheme)");
            throw new RestException(false, 401, "Unauthorized", "Missing or invalid Authorization bearer token.");
        }

        LOG.debug("EntraID authenticate: bearer token present, length={} chars (token value not logged)", token.length());

        final String jwksEndpoint = getRequiredProperty("jwks.endpoint");
        final String authority = getRequiredProperty("authority");
        final String clientId = getRequiredProperty("client.id");

        LOG.debug("EntraID authenticate: config — jwks.endpoint={}, authority={}, client.id (expected audience)={}", jwksEndpoint, authority,
            clientId);

        try {
            logUnverifiedJwtSummary("before signature verify", token);

            final DecodedJWT decodedJwt = validateToken(token, jwksEndpoint, authority, clientId);

            LOG.debug("EntraID authenticate: JWT signature and issuer/audience verification succeeded");

            final String identifierClaim = properties != null && StringUtils.isNotBlank(properties.getProperty("user.identifier.claim"))
                ? properties.getProperty("user.identifier.claim")
                : "email";

            LOG.debug("EntraID authenticate: using identifier claim name={}", identifierClaim);

            final String identifier = decodedJwt.getClaim(identifierClaim).asString();
            if (StringUtils.isBlank(identifier)) {
                LOG.warn("EntraID authenticate: claim '{}' missing or blank on verified token (available claims logged at DEBUG)", identifierClaim);
                logClaimKeysAtDebug(decodedJwt);
                throw new RestException(false, 401, "Unauthorized", "Token missing required claim: " + identifierClaim);
            }

            LOG.debug("EntraID authenticate: resolved identifier from claim '{}' = {}", identifierClaim, identifier);

            final String name = decodedJwt.getClaim("name").asString();
            LOG.debug("EntraID authenticate: name claim = {}", StringUtils.isBlank(name) ? "(absent or blank, will fall back to identifier)" : name);

            final User user = new User();
            user.setUserName(identifier);
            user.setEmail(identifier);
            user.setName(StringUtils.isNotBlank(name) ? name : identifier);
            user.setRoles(new HashSet<>());

            applyDefaultRoles(user);

            user.setModifiedBy(user.getUserName());

            LOG.info("EntraID authenticate: success for userName={}, email={}, roles={}", user.getUserName(), user.getEmail(), user.getRoles());
            LOG.debug("EntraID authenticate: full user object: {}", user);
            return user;

        } catch (final RestException e) {
            if (e.getError() != null) {
                LOG.warn("EntraID authenticate: RestException httpStatus={} error={} message={}", e.getError().getStatus(), e.getError().getError(),
                    e.getError().getMessage());
            } else {
                LOG.warn("EntraID authenticate: RestException with null error payload: {}", e.getMessage());
            }
            throw e;
        } catch (final Exception e) {
            LOG.error("EntraID authenticate: unexpected error during token validation or user build — {}", e.getMessage(), e);
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
            LOG.debug("EntraID extractBearerToken: no ServletRequestAttributes or request in RequestContextHolder");
            return null;
        }

        final String method = requestAttributes.getRequest().getMethod();
        final String uri = requestAttributes.getRequest().getRequestURI();
        LOG.debug("EntraID extractBearerToken: request {} {}", method, uri);

        final String authHeader = requestAttributes.getRequest().getHeader("Authorization");
        if (StringUtils.isBlank(authHeader)) {
            LOG.debug("EntraID extractBearerToken: Authorization header absent or blank");
            return null;
        }

        LOG.debug("EntraID extractBearerToken: Authorization header present, length={} (value not logged)", authHeader.length());

        final String lower = authHeader.toLowerCase();
        if (!lower.startsWith("bearer ")) {
            LOG.debug("EntraID extractBearerToken: Authorization does not start with 'Bearer ' (prefix check only, value not logged)");
            return null;
        }

        final String raw = authHeader.substring(7).trim();
        if (StringUtils.isBlank(raw)) {
            LOG.debug("EntraID extractBearerToken: Bearer prefix present but token part is blank");
            return null;
        }

        LOG.debug("EntraID extractBearerToken: extracted token segment length={}", raw.length());
        return raw;
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
        final String kid = jwt.getKeyId();
        LOG.debug("EntraID validateToken: decoded header, keyId (kid)={}", kid);

        LOG.debug("EntraID validateToken: fetching JWK from {}", jwksEndpoint);
        final JwkProvider provider = new UrlJwkProvider(new URL(jwksEndpoint));
        final Jwk jwk = provider.get(jwt.getKeyId());
        LOG.debug("EntraID validateToken: JWK resolved for kid={}", kid);

        final Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

        final JWTVerifier verifier = JWT.require(algorithm)
            .withIssuer(authority)
            .withAudience(clientId)
            .build();

        LOG.debug("EntraID validateToken: verifying signature, issuer must equal authority, audience must contain clientId");
        final DecodedJWT verified = verifier.verify(token);
        LOG.debug("EntraID validateToken: verify() completed, subject={}", verified.getSubject());
        return verified;
    }

    /**
     * Logs a short summary of the JWT without verifying the signature. Does not log the raw token.
     *
     * @param phase label for the log line
     * @param token the JWT string
     */
    private void logUnverifiedJwtSummary(final String phase, final String token) {

        if (!LOG.isDebugEnabled()) {
            return;
        }
        try {
            final DecodedJWT d = JWT.decode(token);
            final List<String> aud = d.getAudience();
            final Date exp = d.getExpiresAt();
            final Date iat = d.getIssuedAt();
            LOG.debug(
                "EntraID JWT summary ({}): issuer={}, subject={}, audience={}, expiresAt={}, issuedAt={}, keyId={}",
                phase,
                d.getIssuer(),
                d.getSubject(),
                aud,
                exp,
                iat,
                d.getKeyId());
        } catch (final Exception e) {
            LOG.debug("EntraID JWT summary ({}): could not decode token for logging — {}", phase, e.getMessage());
        }
    }

    private void logClaimKeysAtDebug(final DecodedJWT decodedJwt) {

        if (!LOG.isDebugEnabled()) {
            return;
        }
        LOG.debug("EntraID authenticate: JWT claim keys present: {}", decodedJwt.getClaims().keySet());
    }

    /**
     * Applies default roles from configuration for first-time or property-based users.
     *
     * @param user the user
     */
    private void applyDefaultRoles(final User user) {

        if (properties == null) {
            LOG.debug("EntraID applyDefaultRoles: skip — properties null");
            return;
        }

        final String userName = user.getUserName();
        if (StringUtils.isBlank(userName)) {
            LOG.debug("EntraID applyDefaultRoles: skip — user.userName blank");
            return;
        }

        LOG.debug("EntraID applyDefaultRoles: evaluating property lists for user={}", userName);

        final Set<String> rolesToAdd = new HashSet<>();

        addRolesIfConfigured(userName, "users.admin", "all-all-all-admin", rolesToAdd);
        addRolesIfConfigured(userName, "users.author", "all-all-all-author", rolesToAdd);
        addRolesIfConfigured(userName, "users.reviewer", "all-all-all-reviewer", rolesToAdd);

        if (!rolesToAdd.isEmpty()) {
            user.getRoles().addAll(rolesToAdd);
            LOG.info("EntraID applyDefaultRoles: added bootstrap roles {} for user={}", rolesToAdd, userName);
        } else {
            LOG.debug("EntraID applyDefaultRoles: no bootstrap role matches for user={} (check ENTRAID_*_USERS lists)", userName);
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
            LOG.trace("EntraID addRolesIfConfigured: property {} unset or blank", key);
            return;
        }

        final String[] users = list.split(",");
        LOG.debug("EntraID addRolesIfConfigured: key={} rawListLength={} entries (comma-split)", key, users.length);

        for (final String entry : users) {
            final String trimmed = entry.trim();
            if (userName.equalsIgnoreCase(trimmed)) {
                LOG.debug("EntraID addRolesIfConfigured: match user={} against {} entry → role {}", userName, key, role);
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
            LOG.error("EntraID getRequiredProperty: properties bundle is null, requested key={}", key);
            throw new RestException(false, 500, "Server Error",
                "EntraID handler properties not configured for key: " + key);
        }
        final String value = properties.getProperty(key);
        if (StringUtils.isBlank(value)) {
            LOG.error("EntraID getRequiredProperty: missing or blank value for required key={}", key);
            throw new RestException(false, 500, "Server Error",
                "Missing required EntraID handler property: " + key);
        }
        LOG.trace("EntraID getRequiredProperty: key={} present (value length={})", key, value.length());
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
        if (properties == null) {
            LOG.warn("EntraID setProperties: properties set to null");
        } else {
            LOG.info("EntraID setProperties: loaded {} property entries for handler (keys not listed to avoid leaking secrets)",
                properties.size());
            LOG.debug("EntraID setProperties: property key names: {}", properties.stringPropertyNames());
        }
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
            LOG.trace("EntraID getAuthenticateUrl: properties null");
            return null;
        }
        final String endpoint = properties.getProperty("authorization.endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            LOG.debug("EntraID getAuthenticateUrl: returning authorization.endpoint");
            return endpoint;
        }
        final String authority = properties.getProperty("authority");
        LOG.debug("EntraID getAuthenticateUrl: falling back to authority={}", authority);
        return authority;
    }

    /* see superclass */
    @Override
    public String getLogoutUrl() throws Exception {

        if (properties == null) {
            LOG.trace("EntraID getLogoutUrl: properties null");
            return null;
        }
        final String endpoint = properties.getProperty("logout.endpoint");
        if (StringUtils.isNotBlank(endpoint)) {
            LOG.debug("EntraID getLogoutUrl: returning logout.endpoint");
            return endpoint;
        }
        LOG.debug("EntraID getLogoutUrl: logout.endpoint blank");
        return null;
    }

    /* see superclass */
    @Override
    public Set<String> getSystemAdminUserNames() throws Exception {

        if (properties == null || StringUtils.isBlank(properties.getProperty("users.admin"))) {
            LOG.trace("EntraID getSystemAdminUserNames: empty");
            return new HashSet<>();
        }
        final String propertyValue = properties.getProperty("users.admin").trim();
        if (propertyValue.isEmpty()) {
            return new HashSet<>();
        }
        final Set<String> set = Arrays.stream(propertyValue.split("\\s*,\\s*")).collect(Collectors.toSet());
        LOG.debug("EntraID getSystemAdminUserNames: count={}", set.size());
        return set;
    }

    /* see superclass */
    @Override
    public Set<String> getSystemAuthorUserNames() throws Exception {

        if (properties == null || StringUtils.isBlank(properties.getProperty("users.author"))) {
            LOG.trace("EntraID getSystemAuthorUserNames: empty");
            return new HashSet<>();
        }
        final String propertyValue = properties.getProperty("users.author").trim();
        if (propertyValue.isEmpty()) {
            return new HashSet<>();
        }
        final Set<String> set = Arrays.stream(propertyValue.split("\\s*,\\s*")).collect(Collectors.toSet());
        LOG.debug("EntraID getSystemAuthorUserNames: count={}", set.size());
        return set;
    }

    /* see superclass */
    @Override
    public Set<String> getSystemReviewerUserNames() throws Exception {

        if (properties == null || StringUtils.isBlank(properties.getProperty("users.reviewer"))) {
            LOG.trace("EntraID getSystemReviewerUserNames: empty");
            return new HashSet<>();
        }
        final String propertyValue = properties.getProperty("users.reviewer").trim();
        if (propertyValue.isEmpty()) {
            return new HashSet<>();
        }
        final Set<String> set = Arrays.stream(propertyValue.split("\\s*,\\s*")).collect(Collectors.toSet());
        LOG.debug("EntraID getSystemReviewerUserNames: count={}", set.size());
        return set;
    }
}

