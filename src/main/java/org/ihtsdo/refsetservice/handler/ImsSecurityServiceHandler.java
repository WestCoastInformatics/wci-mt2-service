package org.ihtsdo.refsetservice.handler;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.ws.rs.WebApplicationException;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public User authenticate(final String userName, final String password) throws Exception {

        final Cookie imsCookie = SecurityService.getImsCookie();

        // either need an IMS cookie or a password could be from a separate login page
        if (userName == null || (password == null && imsCookie == null)) {
            throw new WebApplicationException("IMS Authentication failed with invalid parameters.");
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

        // TODO remove before next UAT push. added 2/2/2022
        if (user.getUserName().equals("twhalen") || user.getUserName().equals("jefron")) {

            Set<String> timRoles = new HashSet<>();
            timRoles.add("be-bep-all");
            user.setRoles(timRoles);
        }

        user.setModifiedBy(user.getUserName());

        logger.debug("!!!!!!!!!!!!! authenticate user is: " + user);
        return user;
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

}
