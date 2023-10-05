package org.ihtsdo.refsetservice.util;

import java.io.IOException;
import java.net.URL;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.WebApplicationException;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Utility for interacting with JWT tokens.
 */
public final class JwtUtility {

    /** The LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(JwtUtility.class);

    /** The Constant jwks. */
    private static Map<String, PublicKey> keyMap = new HashMap<>();

    /** The Constant DAY. */
    private static final int DAY = 60 * 60 * 24;

    /** The Constant MAX_EXPIRES_IN. */
    private static final int MAX_EXPIRES_IN = DAY;

    /**
     * Instantiates an empty {@link ConfigUtility}.
     */
    private JwtUtility() {

        // n/a
    }

    /**
     * Inits the thread context.
     *
     * @param label the label
     */
    public static void initThreadContext(final String label) {

        ThreadContext.put("user-id", label);
        ThreadContext.put("organization-id", label);
        ThreadContext.put("correlation-id", label);
    }

    /**
     * Returns the expires in.
     *
     * @param hours the hours
     * @return the expires in
     */
    public static long getExpiresIn(final int hours) {

        // 4 hour expiration
        return hours * 60 * 60;
    }

    /**
     * Returns the local jwt.
     *
     * @return the local jwt
     */
    public static String getLocalJwt() {

        return ThreadContext.get("jwt");
    }

    /**
     * Sets the local jwt.
     *
     * @param jwt the jwt
     */
    public static void setLocalJwt(final String jwt) {

        ThreadContext.put("jwt", jwt);
    }

    /**
     * Verify.
     *
     * @param jwt the jwt
     * @throws Exception the exception
     */
    public static void verify(final DecodedJWT jwt) throws Exception {

        verify(jwt, true);
    }

    /**
     * Verfiy the decoded jwt with or without a signature check.
     *
     * @param djwt the djwt
     * @param checkSignature the check signature
     * @throws Exception the exception
     */
    public static void verify(final DecodedJWT djwt, final boolean checkSignature) throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        final String secret = prop.getProperty("jwt.secret");
        if (secret == null) {
            throw new LocalException("Unexpected missing property from application.properties = jwt.secret");
        }
        final String audience = getAudience();
        if (audience == null) {
            throw new LocalException("Unexpected missing audience");
        }
        final Map<String, Claim> claims = djwt.getClaims();

        // Service tokens don't have id claims
        final String userId = getUserId(claims);
        if (userId == null) {
            if (getRole(claims) == null) {
                // Log the error because authorize is called before "try"
                // block
                LOG.error("Non-admin JWT unexpectedly missing ID claim = " + ModelUtility.toJson(claims), 401);
                throw new WebApplicationException("Non-admin JWT unexpectedly missing ID claim");
            }
        } else {
            if (!"admin".equals(userId) && !userId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                LOG.error("JWT ID claim is unexpectedly not a valid UUID/admin= " + userId);
                throw new WebApplicationException("JWT ID claim is unexpectedly not a valid UUID/admin = " + userId, 401);
            }
        }

        // final String aud = djwt.getAudience().get(0);
        // if (!aud.contains(getAudience())) {
        // LOG.error("JWT bad audience = " + aud + ", " + getAudience());
        // throw new WebApplicationException("JWT bad audience = " + aud, 401);
        // }

        final Algorithm algorithm = Algorithm.HMAC256(secret);
        // Reusable verifier instance
        final JWTVerifier verifier = JWT.require(algorithm).acceptLeeway(1).acceptExpiresAt(5).withAudience(getAudience()).build();

        verifier.verify(djwt.getToken());

    }

    /**
     * Verify jwks.
     *
     * @param djwt the djwt
     * @param url the url
     * @throws Exception the exception
     */
    public static void verifyJwks(final DecodedJWT djwt, final String url) throws Exception {

        PublicKey publicKey = null;
        // Check the cache
        if (keyMap.containsKey(url)) {
            publicKey = keyMap.get(url);
        }
        // Otherwise, get the public key again
        else {
            publicKey = getPublicKey(djwt, url);
            LOG.info("  add public key cache entry for = " + url);
            keyMap.put(url, publicKey);
        }
        final Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) publicKey, null);
        try {
            algorithm.verify(djwt);
        } catch (Exception e) {
            // NOTE; if they've switched keys at the URL, this key may no longer
            // be
            // valid in that case, clear the map for this url and try again

            // Get public key again
            final PublicKey publicKey2 = getPublicKey(djwt, url);
            // If not the same, try again
            if (!publicKey.equals(publicKey2)) {
                keyMap.remove(url);
                LOG.info("  remove public key cache entry for = " + url);
                publicKey = publicKey2;
                LOG.info("  add public key cache entry for = " + url);
                keyMap.put(url, publicKey);
                // Try a second time (if this fails, throw the exception)
                algorithm.verify(djwt);
            }
            // Otherwise, throw the exception
            else {
                LOG.info("  public key matches = " + url);
                throw e;
            }
        }

        // Check expiration
        if (djwt.getExpiresAt().before(Calendar.getInstance().getTime())) {
            throw new Exception("Expired token = " + djwt.getExpiresAt());
        }

    }

    /**
     * Returns the public key.
     *
     * @param djwt the djwt
     * @param url the url
     * @return the public key
     * @throws Exception the exception
     */
    private static PublicKey getPublicKey(final DecodedJWT djwt, final String url) throws Exception {

        final UrlJwkProvider provider = new UrlJwkProvider(new URL(url), null, null);
        Jwk jwk = null;
        // IF "kid" is specified in JWT, use it
        if (djwt.getKeyId() != null) {
            jwk = provider.get(djwt.getKeyId());
        }
        // If otherwise there is just a single RSA key, use that
        else if (provider.getAll().stream().filter(k -> k.getType().equals("RSA")).count() == 1) {
            jwk = provider.getAll().stream().filter(k -> k.getType().equals("RSA")).findFirst().get();
        }
        // Otherwise, fail with "unexpected condition"
        else {
            throw new Exception("Expecting either JWT to specify a kid OR " + "provider URL to have exactly one RSA key = " + djwt.getKeyId() + ", "
                + provider.getAll().stream().filter(k -> k.getType().equals("RSA")).count());
        }
        return jwk.getPublicKey();
    }

    /**
     * Returns the wci id.
     *
     * @param claims the claims
     * @return the wci id
     * @throws Exception the exception
     */
    public static String getUserId(final Map<String, Claim> claims) throws Exception {

        Claim claim = claims.get(Claims.ID.getValue());
        if (claim == null) {
            if ("ADMIN".equals(claims.get(Claims.ROLE.getValue()).asString())) {
                return null;
            }

            claim = claims.get(PropertyUtility.getProperties().getProperty("jwt.id.claim"));
            if (claim == null) {
                throw new Exception(
                    "Unexpected missing ID claim = " + Claims.ID.getValue() + ", " + PropertyUtility.getProperties().getProperty("jwt.id.claim"));
            }
            return claim.asString();
        }
        return claim.asString();
    }

    /**
     * Returns the organization id.
     *
     * @param claims the claims
     * @return the organization id
     * @throws Exception the exception
     */
    public static String getOrganizationId(final Map<String, Claim> claims) throws Exception {

        Claim claim = claims.get(Claims.ORG.getValue());
        if (claim == null) {
            return null;
        }
        return claim.asString();
    }

    /**
     * Returns the role.
     *
     * @param claims the claims
     * @return the role
     * @throws Exception the exception
     */
    public static String getRole(final Map<String, Claim> claims) throws Exception {

        Claim claim = claims.get(Claims.ROLE.getValue());
        if (claim == null) {
            return null;
        }
        return claim.asString();
    }

    /**
     * Returns the user id.
     *
     * @param payload the payload
     * @return the user id
     * @throws JsonProcessingException the json processing exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static String getUserId(final String payload) throws JsonProcessingException, IOException {

        if (payload.contains(Claims.ID.getValue())) {
            return payload.replaceFirst(".*" + StringUtility.escapeRegex(Claims.ID.getValue()) + "\"\\s*:\\s*\"([0-9a-zA-Z-]+)\".*", "$1");
        }
        return null;
    }

    /**
     * Returns the payload.
     *
     * @param djwt the djwt
     * @return the payload
     * @throws Exception the exception
     */
    public static Map<String, String> getPayload(final DecodedJWT djwt) throws Exception {

        final Map<String, String> map = new HashMap<>();
        for (final Map.Entry<String, Claim> entry : djwt.getClaims().entrySet()) {
            if (entry.getValue().asString() != null) {
                map.put(entry.getKey(), entry.getValue().asString());
            }
        }
        if (djwt.getIssuedAt() != null) {
            map.put("iat", djwt.getIssuedAt().toString());
        }
        if (djwt.getExpiresAt() != null) {
            map.put("exp", djwt.getExpiresAt().toString());
        }
        return map;
    }

    /**
     * Returns the org id.
     *
     * @param claims the claims
     * @return the org id
     */
    public static String getOrgId(final Map<String, Claim> claims) {

        return claims.get(Claims.ORG.getValue()).asString();
    }

    /**
     * Returns the expiration.
     *
     * @param djwt the djwt
     * @return the expiration
     */
    public static Date getExpiration(final DecodedJWT djwt) {

        return djwt.getExpiresAt();
    }

    /**
     * Mock jwt.
     *
     * @param userId the user id
     * @return the string
     * @throws Exception the exception
     */
    public static String mockJwt(final String userId) throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        return mockJwt(userId, null, null, prop.getProperty("jwt.secret"));
    }

    /**
     * Mock refresh jwt.
     *
     * @param userId the user id
     * @return the string
     * @throws Exception the exception
     */
    public static String mockRefreshJwt(final String userId) throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        return mockJwt(userId, null, "REFRESH", prop.getProperty("jwt.secret"));
    }

    /**
     * Mock account jwt. Used for short-window timeout tokens used in automated emails (like password reset).
     *
     * @param userId the user id
     * @return the string
     * @throws Exception the exception
     */
    public static String mockAccountJwt(final String userId) throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        return mockJwt(userId, null, "ACCOUNT", prop.getProperty("jwt.secret"));
    }

    /**
     * Mock jwt. Normal user JWT - with configurable role.
     *
     * @param userId the wci id
     * @param orgId the org id
     * @return the string
     * @throws Exception the exception
     */
    public static String mockJwt(final String userId, final String orgId) throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        return mockJwt(userId, orgId, null, prop.getProperty("jwt.secret"));
    }

    /**
     * Mock jwt.
     *
     * @param userId the wci id
     * @param orgId the org id
     * @param role the role
     * @return the string
     * @throws Exception the exception
     */
    public static String mockJwt(final String userId, final String orgId, final String role) throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        return mockJwt(userId, orgId, role, prop.getProperty("jwt.secret"));
    }

    /**
     * Mock jwt.
     *
     * @param userId the wci id
     * @param organizationId the organization id
     * @param role the role
     * @param jwtSecret the jwt secret
     * @return the string
     * @throws Exception the exception
     */
    public static String mockJwt(final String userId, final String organizationId, final String role, final String jwtSecret) throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        final String secret = jwtSecret;

        if (secret == null) {
            throw new LocalException("Unexpected missing property from application.properties = jwt.secret");
        }
        final String audience = getAudience();
        if (audience == null) {
            throw new LocalException("Unexpected missing audience");
        }
        final String issuer = prop.getProperty("jwt.issuer");
        if (issuer == null) {
            throw new LocalException("Unexpected missing property from application.properties = jwt.issuer");
        }
        final String idClaimId = Claims.ID.getValue();
        final String orgClaimId = Claims.ORG.getValue();
        final String roleClaimId = Claims.ROLE.getValue();

        // The JWT signature algorithm we will be using to sign the token
        final Algorithm algorithm = Algorithm.HMAC256(secret);
        final Date now = new Date();

        // Set claims
        // .withIssuedAt(now)
        final Builder builder = JWT.create().withAudience(audience).withIssuer(issuer).withClaim(idClaimId, userId)
            .withExpiresAt(new Date((getExpiresIn(24) * 1000) + now.getTime()));

        // Handle org claim
        if (organizationId != null) {
            builder.withClaim(orgClaimId, organizationId);
        }
        if (role != null) {
            builder.withClaim(roleClaimId, role);
        }

        // Builds the JWT and serializes it to a compact, URL-safe string
        return builder.sign(algorithm);
    }

    /**
     * Mock jwt.
     *
     * @param userId the user id
     * @param organizationId the organization id
     * @param role the role
     * @param jwtSecret the jwt secret
     * @param expiresIn the expires in
     * @param claims the claims
     * @return the string
     * @throws Exception the exception
     */
    public static String mockJwt(final String userId, final String organizationId, final String role, final Integer expiresIn, final Map<String, String> claims)
        throws Exception {

        final Properties prop = PropertyUtility.getProperties();
        final String secret = prop.getProperty("jwt.secret");

        if (secret == null) {
            throw new LocalException("Unexpected missing property from config.properties = jwt.secret");
        }
        final String audience = getAudience();
        if (audience == null) {
            throw new LocalException("Unexpected missing audience");
        }
        final String issuer = prop.getProperty("jwt.issuer");
        if (issuer == null) {
            throw new LocalException("Unexpected missing property from config.properties = jwt.issuer");
        }
        final String customClaimId = Claims.ID.getValue();
        if (customClaimId == null) {
            throw new LocalException("Unexpected missing property from config.properties = jwt.customClaimId");
        }
        final String idClaimId = Claims.ID.getValue();
        final String orgClaimId = Claims.ORG.getValue();
        final String roleClaimId = Claims.ROLE.getValue();

        // The JWT signature algorithm we will be using to sign the token
        final Algorithm algorithm = Algorithm.HMAC256(secret);
        final Date now = new Date();

        // Set claims
        // .withIssuedAt(now)
        final Builder builder = JWT.create().withAudience(audience).withIssuer(issuer).withClaim(idClaimId, userId)
            .withExpiresAt(new Date((getExpiresIn(24) * 1000) + now.getTime()));

        // Handle org claim - override with claims parameter
        builder.withClaim(orgClaimId, organizationId);
        builder.withClaim(roleClaimId, role);

        if (claims != null) {
            // Check claims not allowed to be overridden
            for (final String claim : getImmutableClaims()) {
                if (claims.containsKey(claim)) {
                    throw new Exception("Proxy auth unable to overwrite claim = " + claim);
                }
            }
            for (final Map.Entry<String, String> claim : claims.entrySet()) {
                builder.withClaim(claim.getKey(), claim.getValue());
            }
        }
        // if it has been specified, let's add the expiration - 24 hours
        if (expiresIn != null && expiresIn > MAX_EXPIRES_IN) {
            throw new Exception("expiresIn is greater than max allowed value = " + expiresIn);
        }

        // Builds the JWT and serializes it to a compact, URL-safe string
        return builder.sign(algorithm);

    }

    /**
     * Returns the immutable claims.
     *
     * @return the immutable claims
     */
    public static String[] getImmutableClaims() {

        return new String[] {
            "iad", "aud", "iss", "exp"
        };
    }

    /**
     * Rewrite jwt.
     *
     * @param jwt the jwt
     * @param jwtSecret the jwt secret
     * @param expires the expires
     * @return the string
     * @throws Exception the exception
     */
    public static String rewriteJwt(final String jwt, final String jwtSecret, final boolean expires) throws Exception {

        final String secret = jwtSecret;
        if (secret == null) {
            throw new LocalException("Unexpected missing property from application.properties = jwt.secret");
        }

        // The JWT signature algorithm we will be using to sign the token
        final Algorithm algorithm = Algorithm.HMAC256(secret);
        final DecodedJWT djwt = JWT.decode(jwt);

        // Set claims
        final Builder builder = JWT.create();
        if (djwt.getIssuer() != null) {
            builder.withIssuer(djwt.getIssuer());
        }
        if (djwt.getIssuedAt() != null) {
            builder.withIssuedAt(djwt.getIssuedAt());
        }
        if (expires && djwt.getExpiresAt() != null) {
            builder.withExpiresAt(djwt.getExpiresAt());
        }
        if (djwt.getAudience() != null && djwt.getAudience().size() > 0) {
            for (final String aud : djwt.getAudience()) {
                builder.withAudience(aud);
            }
        }
        for (final Map.Entry<String, Claim> entry : djwt.getClaims().entrySet()) {
            if (entry.getValue().asString() != null) {
                builder.withClaim(entry.getKey(), entry.getValue().asString());
            }
        }

        // Builds the JWT and serializes it to a compact, URL-safe string
        return builder.sign(algorithm);
    }

    /**
     * Rewrite jwt no expiration.
     *
     * @param jwt the jwt
     * @return the string
     * @throws Exception the exception
     */
    public static String rewriteJwtNoExpiration(final String jwt) throws Exception {

        return rewriteJwt(jwt, PropertyUtility.getProperties().getProperty("jwt.secret"), true);
    }

    // /**
    // * Authorize.
    // *
    // * @param jwt the jwt
    // * @return the auth context
    // * @throws Exception the exception
    // */
    // public static AuthContext authorize(final String jwt) throws Exception {
    //
    // final AuthContext context = new AuthContext();
    // ThreadContext.put("jwt", jwt);
    // context.setJwt(jwt);
    // // Decode and verify
    // final DecodedJWT djwt = JWT.decode(jwt);
    // JwtUtility.verify(djwt);
    // context.setClaims(getPayload(djwt));
    // final Map<String, Claim> claims = djwt.getClaims();
    // context.setUserId(JwtUtility.getUserId(claims));
    // context.setRole(JwtUtility.getRole(claims));
    // context.setOrganizationId(JwtUtility.getOrganizationId(claims));
    // ThreadContext.put("user-id", context.getUserId());
    // return context;
    // }

    /**
     * Returns the audience.
     *
     * @return the audience
     * @throws Exception the exception
     */
    private static String getAudience() throws Exception {

        return PropertyUtility.getProperties().getProperty("jwt.audience");
    }
}
