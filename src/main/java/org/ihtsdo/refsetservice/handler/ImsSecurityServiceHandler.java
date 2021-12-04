package org.ihtsdo.refsetservice.handler;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.WebApplicationException;

import org.ihtsdo.refsetservice.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implements a security handler that authorizes via IHTSDO authentication.
 */
public class ImsSecurityServiceHandler implements SecurityServiceHandler {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(DefaultSearchHandler.class);

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

        } else {

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode doc = mapper.readTree(password);
            // ex: {"login": "jsmith", "password": null, "firstName": "John", "lastName": "Smith", "email": "", "langKey": null, "roles": ["ROLE_us-crs-requestor"]}
            final JsonNode userDoc = doc.get("userData");

            logger.info("JsonNode userDoc {}", userDoc);

            // Construct user from document
            final User user = new User();

            user.setName(
                    userDoc.get("firstName").asText() + " " + userDoc.get("lastName").asText());
            user.setUserName(userDoc.get("login").asText());
            user.setEmail(userDoc.get("email").asText());
            // user.getRoles().add(User.ROLE_USER);

            final Iterator<JsonNode> roleIterator = userDoc.get("roles").elements();
            final List<String> wciUsers = Arrays.asList("jefron", "twhalen", "twilliams", "wboeger", "ajones", "swhalen", "nmarques", "rwood", "dshapiro");
            
            // boolean authorCredentialsMatched = false;
            while (roleIterator.hasNext()) {

                JsonNode role = roleIterator.next();
                logger.debug("role: " + role.asText());

                /*
                 * if ("ROLE_refset-administrators".equals(role.asText())) {
                 * user.getRoles().add(User.ROLE_ADMIN); }
                 */
                /*
                 * // TODO - !!!!!! JUST FOR TESTING - REMOVE BEFORE PROD !!!!!!
                 * if (user.getUserName().equals("refset-dev")) {
                 * 
                 * logger.
                 * debug(" Using refset-dev creds and making Authour & Reviewer"
                 * ); user.getRoles().add(User.ROLE_AUTHOR);
                 * user.getRoles().add(User.ROLE_REVIEWER);
                 * authorCredentialsMatched = true; break; }
                 */
                
                if ("ROLE_us-crs-requestor".equals(role.asText())) {

                    user.getRoles().add(User.ROLE_REVIEWER);
                    break;
                }
            }
            
            // TODO - ONLY UNTIL IMS ROLES RESOVLED
            if (user.getUserName().equals("refset-dev")) {
                
               user.getRoles().add(User.ROLE_AUTHOR);
               user.getRoles().add(User.ROLE_REVIEWER);
               
            } else if (wciUsers.contains(user.getUserName())) {
                user.getRoles().add(User.ROLE_AUTHOR);
            } else {
                user.getRoles().add(User.ROLE_REVIEWER);
            }

            user.setModifiedBy(user.getUserName());

            logger.debug("!!!!!!!!!!!!! user is: " + user);
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
