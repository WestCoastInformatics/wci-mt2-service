package org.ihtsdo.refsetservice.handler;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.ws.rs.WebApplicationException;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
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

        // password contains the IMS user document
        if (userName == null || password == null) {
            throw new WebApplicationException("IMS Authentication failed with invalid parameters.");
        }

        // this section is if a local login page is used as opposed to IMS single signon
        if (!password.contains("login") && !password.contains("roles")) {

            logger.debug("Demo Password: " + password);

            final User user = new User();
            user.getRoles().add(User.ROLE_USER);

            String passwordText = password.toLowerCase();

            if (passwordText.contains(User.ROLE_ADMIN.toLowerCase())) {
                user.getRoles().add(User.ROLE_ADMIN);
            }

            if (passwordText.contains(User.ROLE_AUTHOR.toLowerCase())) {
                user.getRoles().add(User.ROLE_AUTHOR);
            }

            if (passwordText.contains(User.ROLE_REVIEWER.toLowerCase())) {
                user.getRoles().add(User.ROLE_REVIEWER);
            }

            if (passwordText.contains(User.ROLE_LEAD.toLowerCase())) {
                user.getRoles().add(User.ROLE_LEAD);
            }

            user.setName("Demo " + userName);
            user.setUserName(userName);
            user.setEmail("not used");

            user.setModifiedBy(user.getUserName());
            return user;
        }

        // This is for IMS login
        else {

            final User user = CrowdAPIClient.getUser(userName);
            final Set<String> groupMemberships = CrowdAPIClient.getMembershipsForUser(userName);
            logger.debug("Memberships {}", groupMemberships);

            for (final String role : groupMemberships) {
                if (role.startsWith(rt2RolePrefix)) {
                    user.getRoles().add(role.substring(rt2RolePrefix.length()));
                }
            }

            // TODO remove before next UAT push. added 2/2/2022
            if (user.getUserName().equals("twhalen") || user.getUserName().equals("twilliams2") || user.getUserName().equals("jefron")) {

                Set<String> timRoles = new HashSet<>();
                timRoles.add("be-bep-all");
                user.setRoles(timRoles);
            }

            user.setModifiedBy(user.getUserName());

            logger.debug("!!!!!!!!!!!!! authenticate user is: " + user);
            return user;
        }
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
