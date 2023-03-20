package org.ihtsdo.refsetservice.handler;

import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implements a security handler that authorizes via IHTSDO authentication.
 */
public class ImsSecurityServiceHandler implements SecurityServiceHandler {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(DefaultSearchHandler.class);

    /** The logger. */
    private static final String rt2RolePrefix = "rt2-";

    /** The properties. */
    @SuppressWarnings("unused")
    private Properties properties;

    /* see superclass */
    @Override
    public User authenticate(final String userName) throws Exception {

        final boolean authenticated = checkImsLogin(userName);

        if (userName == null || !authenticated) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This user is not authenticated with IMS.");
        }

        // This is for IMS login
        final User user = CrowdAPIClient.getUser(userName);
        final Set<String> groupMemberships = CrowdAPIClient.getMembershipsForUser(userName);
        logger.debug("Memberships {}", groupMemberships);

        for (final String role : groupMemberships) {
            if (role.startsWith(rt2RolePrefix)) {
                user.getRoles().add(role.substring(rt2RolePrefix.length()));
            }
        }

        if (userName.equals("twhalen")) {

            user.getRoles().clear();
            user.getRoles().add("snomedctus-all-viewer");
            user.getRoles().add("snomedctse-inrp-reviewer");
            user.getRoles().add("snomedctse-inrp-author");
        }

        user.setModifiedBy(user.getUserName());

        logger.debug("authenticate user is: " + user);
        return user;
    }

    /**
     * Calls an IMS endpoint to make sure user is authenticated.
     *
     * @param userName The userName passed in to the authenticate call
     * @return the response
     * @throws Exception the exception
     */
    protected boolean checkImsLogin(final String userName) throws Exception {

        final String url = getAuthenticateUrl() + "account";
        boolean authenticated = false;
        final Cookie imsCookie = SecurityService.getImsCookie();

        if (imsCookie == null) {
            return false;
        }

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final javax.ws.rs.core.Cookie newCookie = new javax.ws.rs.core.Cookie(imsCookie.getName(), imsCookie.getValue());

        try (Response response = target.request(MediaType.APPLICATION_JSON).cookie(newCookie).get()) {

            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                final String resultString = response.readEntity(String.class);
                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());
                final String imsUserName = root.get("login").asText();

                // make sure that the passed in user name is the same as what IMS has authenticated
                if (imsUserName.equals(userName)) {
                    authenticated = true;
                }
            }

        } catch (Exception e) {
            logger.error("IMS Authentication error: {} ", url, e);
            throw e;
        }

        return authenticated;
    }

    /* see superclass */
    @Override
    public boolean timeoutUser(final String user) {

        // Never timeout user
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

        return "IHTSDO Identity Management Service handler";
    }

    /* see superclass */
    @Override
    public String getAuthenticateUrl() throws Exception {

        return PropertyUtility.getProperty("security.handler.IMS.url");
    }

    /* see superclass */
    @Override
    public String getLogoutUrl() throws Exception {

        return PropertyUtility.getProperty("security.handler.IMS.url.logout");
    }

}
