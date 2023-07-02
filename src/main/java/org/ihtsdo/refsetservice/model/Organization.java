/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
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

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
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
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.micrometer.core.instrument.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents an Organization.
 */
@Entity
@Table(name = "organizations")
@Schema(description = "Represents an organization.")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class Organization extends AbstractHasModified implements Copyable<Organization>, ValidateCrud<Organization> {

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The description. */
    @Column(nullable = true, length = 4000)
    private String description;

    /** email for primary contact. */
    @Column(nullable = true, length = 255)
    private String primaryContactEmail;

    /** The members. */
    @ManyToMany(fetch = FetchType.LAZY, cascade = {
        CascadeType.ALL
    })
    @JoinTable(name = "organization_members", joinColumns = {
        @JoinColumn(name = "organization_id")
    }, inverseJoinColumns = {
        @JoinColumn(name = "user_id")
    })
    @Fetch(FetchMode.JOIN)
    private Set<User> members;

    /** The icon uri. */
    @Column(nullable = true, length = 255)
    private String iconUri;

    /** The of roles for this project. */
    @Transient
    private List<String> roles;

    /**
     * Instantiates an empty {@link Organization}.
     */
    public Organization() {

        // n/a
    }

    /**
     * Instantiates a {@link Organization} from the specified parameters.
     *
     * @param other the other
     */
    public Organization(final Organization other) {

        populateFrom(other);
    }

    /**
     * Instantiates a {@link Organization} from the specified parameters.
     *
     * @param name the value
     */
    public Organization(final String name) {

        this.name = name;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    @Override
    public void populateFrom(final Organization other) {

        super.populateFrom(other);
        name = other.getName();
        description = other.getDescription();
        primaryContactEmail = other.getPrimaryContactEmail();
        iconUri = other.iconUri;
        members = other.getMembers();
        roles = other.getRoles();
    }

    /**
     * Patch from.
     *
     * @param other the other
     */
    @Override
    public void patchFrom(final Organization other) {

        // Only these field can be patched
        name = other.getName();
        description = other.getDescription();
        primaryContactEmail = other.getPrimaryContactEmail();
        roles = other.getRoles();
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
     * Returns the primary contact email.
     *
     * @return the primary contact email
     */
    public String getPrimaryContactEmail() {

        return primaryContactEmail;
    }

    /**
     * Sets the primary contact email.
     *
     * @param primaryContactEmail the primary contact email
     */
    public void setPrimaryContactEmail(final String primaryContactEmail) {

        this.primaryContactEmail = primaryContactEmail;
    }

    /**
     * Returns the members.
     *
     * @return Members (users) of the organization
     */
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    @JsonIgnoreProperties("organizations")
    @JsonSerialize(contentAs = User.class)
    @JsonDeserialize(contentAs = User.class)
    public Set<User> getMembers() {

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
    public void setMembers(final Set<User> members) {

        this.members = members;
    }

    /**
     * Returns the icon URI.
     *
     * @return the icon URI
     */
    public String getIconUri() {

        return iconUri;
    }

    /**
     * Sets the icon URI.
     *
     * @param iconUri the icon uri
     */
    public void setIconUri(final String iconUri) {

        this.iconUri = iconUri;
    }

    /**
     * Returns the roles.
     *
     * @return the roles
     */
    @JsonGetter()
    public List<String> getRoles() {

        if (roles == null) {

            roles = new ArrayList<>();
        }

        return roles;
    }

    /**
     * Sets the roles.
     *
     * @param roles the roles
     */
    public void setRoles(final List<String> roles) {

        this.roles = roles;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((iconUri == null) ? 0 : iconUri.hashCode());
        result = prime * result + ((members == null) ? 0 : members.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((primaryContactEmail == null) ? 0 : primaryContactEmail.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
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
        if (!(obj instanceof Organization)) {
            return false;
        }

        Organization other = (Organization) obj;

        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (iconUri == null) {
            if (other.iconUri != null) {
                return false;
            }
        } else if (!iconUri.equals(other.iconUri)) {
            return false;
        }
        if (members == null) {
            if (other.members != null) {
                return false;
            }
        } else if (!members.equals(other.members)) {
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

        // n/a
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

        if (StringUtils.isBlank(getPrimaryContactEmail())) {

            throw new Exception("Unexpected null/empty primary contact email");
        }

    }

    /* see superclass */
    @Override
    public void validateUpdate(final Organization other) throws Exception {

        if (StringUtils.isBlank(getId())) {

            throw new Exception("Unexpected null/empty id");
        }

        if (StringUtils.isBlank(getName())) {

            throw new Exception("Unexpected null/empty name");
        }

        if (StringUtils.isBlank(getPrimaryContactEmail())) {

            throw new Exception("Unexpected null/empty primary contact email");
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
