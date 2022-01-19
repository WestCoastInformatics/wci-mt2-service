
package org.ihtsdo.refsetservice.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a user and roles.
 * 
 */
@Entity
@Table(name = "users")
@Schema(description = "Represents a message to send (and possibly have confirmed)")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class User extends AbstractHasModified implements Comparable<User> {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(User.class);

    /** The username. */
    @Column(nullable = false, unique = true, length = 250)
    private String userName;

    /** The user's full name. */
    @Column(nullable = false, length = 250)
    private String name;

    /** The user's email. */
    @Column(nullable = false, length = 250)
    private String email;

    /** The auth token. */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String authToken;

    /** A list of the roles this user has. */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> roles = new HashSet<>();

    /** The admin role. */
    public static final String ROLE_ADMIN = "ADMIN";

    /** The lead role. */
    public static final String ROLE_LEAD = "LEAD";

    /** The reviewer role. */
    public static final String ROLE_REVIEWER = "REVIEWER";

    /** The author role. */
    public static final String ROLE_AUTHOR = "AUTHOR";

    /** The user role. */
    public static final String ROLE_USER = "USER";

    /** The user role. */
    public static final String ROLE_VIEWER = "VIEWER";

    /**
     * Instantiates an empty {@link User}.
     */
    public User() {

        // n/a
    }

    /**
     * Instantiates a {@link User} from the specified parameters.
     *
     * @param userName the username
     * @param name the user's full name
     * @param email the user's email
     * @param roles the roles this user has
     */
    public User(final String userName, final String name, final String email, final Set<String> roles) {

        this.userName = userName;
        this.name = name;
        this.email = email;
        this.roles = roles;
    }

    /**
     * Instantiates a {@link User} from the specified parameters.
     *
     * @param other the other
     */
    public User(final User other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final User other) {

        super.populateFrom(other);
        userName = other.getUserName();
        name = other.getName();
        email = other.getEmail();
        roles = other.getRoles();
    }

    /**
     * Returns the userName.
     *
     * @return the userName
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "nameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getUserName() {

        return userName;
    }

    /**
     * Sets the userName.
     *
     * @param userName the userName
     */
    public void setUserName(final String userName) {

        this.userName = userName;
    }

    /**
     * Returns the full name.
     *
     * @return the name
     */
    public String getName() {

        return name;
    }

    /**
     * Sets the full name.
     *
     * @param name the name
     */
    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Returns the email.
     *
     * @return the email
     */
    public String getEmail() {

        return email;
    }

    /**
     * Sets the email.
     *
     * @param email the email
     */
    public void setEmail(final String email) {

        this.email = email;
    }

    /**
     * Sets the authentication token.
     * 
     * @return
     */
    public String getAuthToken() {

        return authToken;
    }

    /**
     * Returns the authentication token.
     * 
     * @param authToken
     */
    public void setAuthToken(String authToken) {

        this.authToken = authToken;
    }

    /**
     * Gets the roles.
     *
     * @return the roles
     */
    // @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public Set<String> getRoles() {

        if (roles == null) {

            roles = new HashSet<>();
        }

        return roles;
    }

    /**
     * Sets the roles.
     *
     * @param roles the roles to set
     */
    public void setRoles(Set<String> roles) {

        this.roles = roles;
    }

    /**
     * Check if the user has the specified role on the refset.
     *
     * @param roleToCheck the role to look for
     * @param project the project to check permissions against
     * @return if the user has the specified role on the refset
     * @throws Exception the exception
     */
    public boolean doesUserHavePermission(final String roleToCheck, final Project project) throws Exception {

        String editionName = project.getOrganization().getEdition().getShortName().toLowerCase();

        if (!project.getOrganization().getEdition().getShortName().equals("SNOMEDCT")) {

            editionName = editionName.replaceFirst("SNOMEDCT-?", "").toLowerCase();
        }

        final String lowerCasedRoleToCheck = roleToCheck.toLowerCase();

        for (final String role : roles) {

            final String lowerCasedRole = role.toLowerCase();
            final int indexFirstHyphen = lowerCasedRole.indexOf("-");
            final String editionPart = lowerCasedRole.substring(0, indexFirstHyphen);
            // logger.debug("******** doesUserHavePermission editionPart: " + editionPart);

            // first check the edition permissions
            if (editionPart.equals("all") || editionPart.equals(editionName)) {

                final String projectPart = lowerCasedRole.substring(indexFirstHyphen + 1, lowerCasedRole.indexOf("-", indexFirstHyphen + 1));
                final String projectName = project.getName().toLowerCase().replace(" ", "_");
                // logger.debug("******** doesUserHavePermission projectPart: " + projectPart);

                // then check the project level permissions
                if (projectPart.equals("all") || projectPart.equals(projectName)) {

                    // logger.debug("******** doesUserHavePermission lowerCasedRole: " + lowerCasedRole + " ; lowerCasedRoleToCheck: " + lowerCasedRoleToCheck);
                    // last check for the role or if they have any permission at this level they have the VIEWER role
                    if (lowerCasedRole.endsWith("-" + lowerCasedRoleToCheck) || roleToCheck.equals(ROLE_VIEWER)) {

                        return true;
                    }

                }

            }

        }

        return false;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((userName == null) ? 0 : userName.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((email == null) ? 0 : email.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());

        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    /* see superclass */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {

            return true;
        }

        if (obj == null) {

            return false;
        }

        if (getClass() != obj.getClass()) {

            return false;
        }

        final User other = (User) obj;

        if (userName == null) {

            if (other.userName != null) {

                return false;
            }

        } else if (!userName.equals(other.userName)) {

            return false;
        }

        if (name == null) {

            if (other.name != null) {

                return false;
            }

        } else if (!name.equals(other.name)) {

            return false;
        }

        if (email == null) {

            if (other.email != null) {

                return false;
            }

        } else if (!email.equals(other.email)) {

            return false;
        }

        if (roles == null) {

            if (other.roles != null) {

                return false;
            }

        } else if (!roles.equals(other.roles)) {

            return false;
        }

        return true;
    }

    /**
     * Compare to.
     *
     * @param o the o
     * @return the int
     */
    /* see superclass */
    @Override
    public int compareTo(final User o) {

        // Handle null
        return (name + roles.toString()).compareToIgnoreCase(o.getName() + o.getRoles().toString());
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
