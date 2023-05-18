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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.micrometer.core.instrument.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a Team.
 */
@Entity
@Table(name = "teams")
@Schema(description = "Represents a team with organization, roles and members (users).")
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

    /**  email for primary contact. */
    @Column(nullable = true, length = 255)
    private String primaryContactEmail;

    /**  roles for team. */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> roles;

    /** The members. (users by name) */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> members;

    /**  The member list. */
    @Transient
    private List<User> memberList;
    
    /** The user's roles for this team. */
    @Transient
    private List<String> userRoles;

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
        primaryContactEmail = other.getPrimaryContactEmail();
        organization = other.getOrganization();
        roles = other.getRoles();
        memberList = other.getMemberList();
        members = other.getMembers();
        userRoles = other.getUserRoles();
    }

    /**
     * Patch from.
     *
     * @param other the other
     */
    public void patchFrom(final Team other) {

        // super.populateFrom(other);
        // Only these field can be patched
        name = other.getName();
        description = other.getDescription();
        primaryContactEmail = other.getPrimaryContactEmail();
        organization = other.getOrganization();
        roles = other.getRoles();
        memberList = other.getMemberList();
        members = other.getMembers();
        userRoles = other.getUserRoles();
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
     * Returns the organization ID.
     *
     * @return the organization ID
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "organization")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getOrganizationId() {

        return organization == null ? null : organization.getId();
    }

    /**
     * Sets the organization ID.
     *
     * @param organizationId the organization ID to set
     */
    public void setOrganizationId(final String organizationId) {

        if (organization == null) {
            this.organization = new Organization();
        }
        this.organization.setId(organizationId);
    }

    /**
     * Returns the roles available to the team.
     *
     * @return the roles
     */
    @JsonGetter()
    public Set<String> getRoles() {

        if (roles == null) {
            roles = new HashSet<>();
        }

        return roles;
    }

    /**
     * Sets the roles.
     *
     * @param roles the roles
     */
    public void setRoles(final Set<String> roles) {

        this.roles = roles;
    }

    /**
     * Returns the members.
     *
     * @return the members
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public Set<String> getMembers() {

        if (members == null) {
            members = new HashSet<>();
        }

        return members;
    }

    /**
     * Sets the members.
     *
     * @param members the members
     */
    public void setMembers(final Set<String> members) {

        this.members = members;
    }

    /**
     * Returns the primary contact email.
     *
     * @return the primaryContactEmail
     */
    public String getPrimaryContactEmail() {

        return primaryContactEmail;
    }

    /**
     * Sets the primary contact email.
     *
     * @param primaryContactEmail the primaryContactEmail
     */
    public void setPrimaryContactEmail(final String primaryContactEmail) {

        this.primaryContactEmail = primaryContactEmail;
    }

    /**
     * Returns the member list.
     *
     * @return the member list
     */
    @JsonGetter()
    public List<User> getMemberList() {

        if (memberList == null) {
            memberList = new ArrayList<>();
        }
        return memberList;
    }

    /**
     * Sets the member list.
     *
     * @param memberList the member list
     */
    public void setMemberList(final List<User> memberList) {

        this.memberList = memberList;
    }
    
    /**
     * Returns a user's roles on this team .
     *
     * @return the user's roles
     */
    @JsonGetter()
    public List<String> getUserRoles() {

        if (userRoles == null) {
            userRoles = new ArrayList<>();
        }

        return userRoles;

    }

    /**
     * Sets the user's roles for this team.
     *
     * @param userRoles the user's roles
     */
    public void setUserRoles(final List<String> userRoles) {
        this.userRoles = userRoles;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((members == null) ? 0 : members.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((organization == null) ? 0 : organization.hashCode());
        result = prime * result + ((primaryContactEmail == null) ? 0 : primaryContactEmail.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
        result = prime * result + ((memberList == null) ? 0 : memberList.hashCode());
        result = prime * result + ((members == null) ? 0 : members.hashCode());
        result = prime * result + ((userRoles == null) ? 0 : userRoles.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(final Object obj) {

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
        if (members == null) {
            if (other.members != null) {
                return false;
            }
        } else if (!members.equals(other.members)) {
            return false;
        }
        if (memberList == null) {
            if (other.memberList != null) {
                return false;
            }
        } else if (!memberList.equals(other.memberList)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (organization == null) {
            if (other.organization != null) {
                return false;
            }
        } else if (!organization.equals(other.organization)) {
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
        if (userRoles == null) {
            if (other.userRoles != null) {
                return false;
            }
        } else if (!userRoles.equals(other.userRoles)) {
            return false;
        }
        return true;
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
    @Override
    public void lazyInit() {

        // TODO Auto-generated method stub
    }

    /* see superclass */
    @Override
    public void validateAdd() throws Exception {

        if (getId() != null) {
            throw new Exception("Unexpected non-null id");
        }
        if (StringUtils.isBlank(getName())) {
            throw new Exception("Unexpected null/empty name");
        }
        if (getRoles() == null || getRoles().isEmpty()) {
            throw new Exception("Unexpected null/empty roles");
        }
    }

    /* see superclass */
    @Override
    public void validateUpdate(final Team other) throws Exception {

        if (StringUtils.isBlank(getId())) {
            throw new Exception("Unexpected null/empty id");
        }
        if (StringUtils.isBlank(getName())) {
            throw new Exception("Unexpected null/empty name");
        }
        if (getRoles() == null || getRoles().isEmpty()) {
            throw new Exception("Unexpected null/empty roles");
        }
    }

    /* see superclass */
    @Override
    public void validateDelete() throws Exception {

        if (StringUtils.isBlank(getId())) {
            throw new Exception("Unexpected null/empty id");
        }
    }
}
