/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a Team.
 */
@Entity
@Table(name = "teams")
@Schema(description = "Represents a team with organization, roles and members (users).")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class Team extends AbstractHasModified implements Copyable<Team>, ValidateCrud<Team> {

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The description. */
    @Column(nullable = true, length = 4000)
    private String description;

    /** The owning organization. */
    @ManyToOne(targetEntity = Organization.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Organization organization;

    /** email for primary contact */
    @Column(nullable = true, length = 255)
    private String primaryContactEmail;

    /** roles for team */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> roles;

    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> members;

    /**
     * Instantiates an empty {@link Team}.
     */
    public Team() {

        // n/a
    }

    /**
     * Instantiates a {@link Team} from the specified parameters.
     *
     * @param other the other
     */
    public Team(final Team other) {

        populateFrom(other);
    }

    /**
     * Instantiates a {@link Team} from the specified parameters.
     *
     * @param name the value
     */
    public Team(final String name) {

        this.name = name;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Team other) {

        super.populateFrom(other);
        name = other.getName();
        description = other.getDescription();
    }

    /**
     * Patch from.
     *
     * @param other the other
     */
    public void patchFrom(final Team other) {

        super.populateFrom(other);
        // Only these field can be patched
        name = other.getName();
        description = other.getDescription();
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "nameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
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

    /**
     * Returns the description.
     *
     * @return the description
     */
    public String getDescription() {

        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description to set
     */
    public void setDescription(final String description) {

        this.description = description;
    }

    /**
     * Gets the organization.
     *
     * @return the organization
     */
    public Organization getOrganization() {

        return organization;
    }

    /**
     * Sets the organization.
     *
     * @param organization the organization to set
     */
    public void setOrganization(final Organization organization) {

        this.organization = organization;
    }

    /**
     * @return the roles
     */
    @JsonGetter()
    public Set<String> getRoles() {

        return (roles != null) ? roles : new HashSet<>();
    }

    /**
     * @param roles the roles
     */
    public void setRoles(Set<String> roles) {

        this.roles = roles;
    }

    /**
     * @return the members
     */
    @JsonGetter()
    public Set<String> getMembers() {

        return (members != null) ? members : new HashSet<>();
    }

    /**
     * @param members the members
     */
    public void setMembers(Set<String> members) {

        this.members = members;
    }

    /**
     * @return the primaryContactEmail
     */
    public String getPrimaryContactEmail() {

        return primaryContactEmail;
    }

    /**
     * @param primaryContactEmail the primaryContactEmail
     */
    public void setPrimaryContactEmail(String primaryContactEmail) {

        this.primaryContactEmail = primaryContactEmail;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((primaryContactEmail == null) ? 0 : primaryContactEmail.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
        result = prime * result + ((members == null) ? 0 : roles.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!super.equals(obj)) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final Team other = (Team) obj;

        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }

        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }

        if (primaryContactEmail == null) {
            if (other.primaryContactEmail != null) {
                return false;
            }
        } else if (!primaryContactEmail.equals(other.primaryContactEmail)) {
            return false;
        }

        if (roles == null) {
            if (other.roles != null) {
                return false;
            }
        } else if (!roles.equals(other.roles)) {
            return false;
        }

        if (members == null) {
            if (other.members != null) {
                return false;
            }
        } else if (!members.equals(other.members)) {
            return false;
        }

        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "Team [name=" + name + ", description=" + description + ", organization=" + organization + ", primaryContactEmail=" + primaryContactEmail + ", roles=" + roles + ", members=" + members
            + "]";
    }

    /* see superclass */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub
    }

    /* see superclass */
    @Override
    public void validateAdd(AuthContext context) throws Exception {
        // TODO validate add
    }

    /* see superclass */
    @Override
    public void validateUpdate(AuthContext context, Team other) throws Exception {
        // TODO validate update
    }

    /* see superclass */
    @Override
    public void validateDelete(AuthContext context) throws Exception {
        // TODO valiidate delete
    }
}
