package org.ihtsdo.refsetservice.handler;

import java.util.Iterator;
import java.util.Properties;

import javax.ws.rs.WebApplicationException;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityServiceHandler;
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

		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode doc = mapper.readTree(password);

		logger.info("User is {}", userName);

		// e.g.
		// {
		// "login": "jsmith",
		// "password": null,
		// "firstName": "John",
		// "lastName": "Smith",
		// "email": "***REMOVED***",
		// "langKey": null,
		// "roles": [
		// "ROLE_confluence-users",
		// "ROLE_ihtsdo-ops-admin",
		// "ROLE_ihtsdo-sca-author",
		// "ROLE_ihtsdo-tba-author",
		// "ROLE_ihtsdo-tech-group",
		// "ROLE_ihtsdo-users",
		// "ROLE_jira-developers",
		// "ROLE_jira-users",
		// "ROLE_mapping-dev-team"
		// ]
		// }

		// Construct user from document
		final User user = new User();
		user.setName(doc.get("firstName").asText() + " " + doc.get("lastName").asText());
		user.setUserName(doc.get("login").asText());
		user.setEmail(doc.get("email").asText());
		user.getRoles().add(User.ROLE_USER);

		final Iterator<JsonNode> iter = doc.get("roles").elements();
		while (iter.hasNext()) {
			JsonNode role = iter.next();
			if ("ROLE_refset-administrators".equals(role.asText())) {
				user.getRoles().add(User.ROLE_ADMIN);
			}
			if (!user.getRoles().contains(User.ROLE_ADMIN) && "ROLE_refset-users".equals(role.asText())) {
				user.getRoles().add(User.ROLE_USER);
			}
		}

		user.setModifiedBy(user.getUserName());
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
