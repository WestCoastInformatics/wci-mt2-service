package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A JPA-enabled implementation of {@link MapUser}.
 */
@Entity
@Table(name = "map_users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "userName"
    })
})
@JsonIgnoreProperties(ignoreUnknown = true, value = {
    "hibernateLazyInitializer", "handler"
})
@Indexed
public class MapUser extends AbstractHasModified {

    /** The user name. */
    @Column(nullable = false, unique = true)
    private String userName;

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The email. */
    @Column(nullable = false)
    private String email;

    /** The application role. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MapUserRole applicationRole;

    /** The team. */
    @Column(nullable = true)
    private String team;

    // Not a field
    /** The auth token. */
    @Transient
    private String authToken;

    /**
     * The default constructor.
     */
    public MapUser() {

    }

    /**
     * Instantiates a new map user jpa.
     *
     * @param mapUser the map user
     */
    public MapUser(final MapUser mapUser) {

        super();
        this.userName = mapUser.getUserName();
        this.name = mapUser.getName();
        this.email = mapUser.getEmail();
        this.team = mapUser.getTeam();
        this.applicationRole = mapUser.getApplicationRole();
        this.authToken = mapUser.getAuthToken();
    }

    /**
     * Returns the user name.
     *
     * @return the user name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getUserName() {

        return userName;
    }

    /**
     * Sets the user name.
     *
     * @param username the user name
     */
    public void setUserName(final String username) {

        this.userName = username;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getName() {

        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(final String name) {

        this.name = name;
    }

    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getEmail() {

        return email;
    }

    public void setEmail(final String email) {

        this.email = email;
    }

    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getTeam() {

        return team;
    }

    public void setTeam(final String team) {

        this.team = team;
    }

    /**
     * Returns the application role.
     *
     * @return the application role
     */
    public MapUserRole getApplicationRole() {

        return applicationRole;
    }

    /**
     * Sets the application role.
     *
     * @param role the application role
     */
    public void setApplicationRole(final MapUserRole role) {

        this.applicationRole = role;
    }

    /* see superclass */
    public void setAuthToken(final String authToken) {

        this.authToken = authToken;
    }

    /* see superclass */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

    /* see superclass */
    public String getAuthToken() {

        return authToken;
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((applicationRole == null) ? 0 : applicationRole.hashCode());
        result = prime * result + ((email == null) ? 0 : email.hashCode());
        result = prime * result + ((team == null) ? 0 : team.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((userName == null) ? 0 : userName.hashCode());
        return result;
    }

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
        final MapUser other = (MapUser) obj;
        if (applicationRole != other.applicationRole) {
            return false;
        }
        if (email == null) {
            if (other.email != null) {
                return false;
            }
        } else if (!email.equals(other.email)) {
            return false;
        }
        if (team == null) {
            if (other.team != null) {
                return false;
            }
        } else if (!team.equals(other.team)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (userName == null) {
            if (other.userName != null) {
                return false;
            }
        } else if (!userName.equals(other.userName)) {
            return false;
        }
        return true;
    }

    @Override
    public void lazyInit() {

        // TODO Auto-generated method stub
        
    }

}
