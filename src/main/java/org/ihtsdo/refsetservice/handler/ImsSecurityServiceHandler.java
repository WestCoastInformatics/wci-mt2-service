package org.ihtsdo.refsetservice.handler;

import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implements a security handler that authorizes via IHTSDO authentication.
 */
public class ImsSecurityServiceHandler implements SecurityServiceHandler {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(DefaultSearchHandler.class);
    
    /** The logger. */
    private static final String rt2RolePrefix = "ROLE_rt2-";

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

//            String cookie = null;
//            String imsServerName = null;
//            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
//
//            if (requestAttributes == null || requestAttributes.getRequest() == null) {
//                throw new WebApplicationException("IMS Authentication failed with invalid parameters.");
//            }
//
//            ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath();
//            Cookie[] cookies = requestAttributes.getRequest().getCookies();
//            final URL requestURL = new URL(requestAttributes.getRequest().getRequestURL().toString());
//            final String protocol = requestURL.getProtocol() + "://";
//            
//            if (cookies != null) {
//
//                HttpServletResponse response = ((ServletRequestAttributes) requestAttributes).getResponse();
//
//                for (int i = 0; i < cookies.length; i++) {
//
//                    if (cookies[i].getName().contains("ims-ihtsdo")) {
//
//                        logger.debug("authenticate ims-ihtsdo cookie: " + ModelUtility.toJson(cookies[i]));
//                        cookie = ModelUtility.toJson(cookies[i]).toString(); 
//                        imsServerName = cookies[i].getName().replace("-ihtsdo", cookies[i].getDomain()) + ".org";
//                        break; 
//                    }
//                }
//            }
//            
//            if (cookies == null) {
//                throw new WebApplicationException("IMS Authentication failed with invalid parameters.");
//            }
//            
//            final Client client = ClientBuilder.newClient();
//            final WebTarget target = client.target(protocol + imsServerName + "/api/account");
//              
//            final Response response = target.request("application/json").header("Cookie", cookie).get();
//            final String resultString = response.readEntity(String.class); 
            
            final User user = new User();
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode imsNode = mapper.readTree(password);
            final JsonNode userNode = imsNode.get("userData");
            // ex: {"login": "jsmith", "roles": ["ROLE_rt2-<CODE SYSTEM>-<PROJECT>-<ROLE>", "ROLE_rt2-all-all-author", "ROLE_rt2-us-training-reviewer"]}

            logger.info("authenticate userNode: ", userNode);

            user.setName(userNode.get("firstName").asText() + " " + userNode.get("lastName").asText());
            user.setUserName(userNode.get("login").asText());
            user.setEmail(userNode.get("email").asText());

            final Iterator<JsonNode> roleIterator = userNode.get("roles").elements();
            
            
            // boolean authorCredentialsMatched = false;
            while (roleIterator.hasNext()) {

                JsonNode roleNode = roleIterator.next();
                String role = roleNode.asText();
                
                logger.debug("role: " + role);

                if (role.startsWith(rt2RolePrefix)) {
                    user.getRoles().add(role.substring(rt2RolePrefix.length()));
                }
            }
            
            // TODO remove before next UAT push. added 2/2/2022
            if (user.getUserName().equals("twhalen") || user.getUserName().equals("twilliams2")) {
                
                Set<String> timRoles = new HashSet<>();
                timRoles.add("be-all-all");
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
