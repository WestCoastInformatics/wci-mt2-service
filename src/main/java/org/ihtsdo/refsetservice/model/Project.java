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
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.ihtsdo.refsetservice.sync.SyncDataInitializer;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.micrometer.core.instrument.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a project.
 */
@Entity
@Table(name = "projects")
@Schema(description = "Represents a project")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class Project extends AbstractHasModified implements Copyable<Project>, ValidateCrud<Project> {

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

    /** The private flag. */
    @Column(nullable = false)
    private boolean privateProject;

    /** email for primary contact. */
    @Column(nullable = true, length = 255)
    private String primaryContactEmail;

    /** The crowd identifier for this project. */
    @Column(nullable = true)
    private String crowdProjectId;

    /** The of teams ids for this project. */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> teams;

    /** The of roles for this project. */
    @Transient
    private List<String> roles;

    /**  The member list. */
    @Transient
    private List<User> memberList;

    /**
     * Instantiates an empty {@link Project}.
     */
    public Project() {

        // n/a
    }

    /**
     * Instantiates a {@link Project} from the specified parameters.
     *
     * @param other the other
     */
    public Project(final Project other) {

        populateFrom(other);
    }

    /**
     * Instantiates a {@link Project} from the specified parameters.
     *
     * @param name the name
     * @param organization the organization
     */
    public Project(final String name, final Organization organization) {

        this.name = name;
        this.organization = organization;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Project other) {

        super.populateFrom(other);
        name = other.getName();
        organization = other.getOrganization();
        description = other.getDescription();
        privateProject = other.isPrivateProject();
        roles = other.getRoles();
        crowdProjectId = other.getCrowdProjectId();
        primaryContactEmail = other.getPrimaryContactEmail();
        teams = other.getTeams();
    }

    /* see superclass */
    @Override
    public void patchFrom(final Project other) {

        // super.populateFrom(other);
        name = other.getName();
        description = other.getDescription();
        privateProject = other.isPrivateProject();
        roles = other.getRoles();
        crowdProjectId = other.getCrowdProjectId();
        primaryContactEmail = other.getPrimaryContactEmail();
        teams = other.getTeams();
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
     * Gets the description.
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
    @IndexedEmbedded(targetType = Organization.class)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
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
     * Gets the crowd project/group id.
     *
     * @return the crowdProjectId
     */
    public String getCrowdProjectId() {

        return crowdProjectId;
    }

    /**
     * Sets the crowdProjectId.
     *
     * @param crowdProjectId the crowdProjectId to set
     */
    public void setCrowdProjectId(final String crowdProjectId) {

        this.crowdProjectId = crowdProjectId;
    }

    /**
     * Checks if is private project.
     *
     * @return is the project private
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public boolean isPrivateProject() {

        return privateProject;
    }

    /**
     * Sets the private project.
     *
     * @param privateProject the private flag to set
     */
    public void setPrivateProject(final boolean privateProject) {

        this.privateProject = privateProject;
    }

    /**
     * Returns the teams.
     *
     * @return the teams
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public Set<String> getTeams() {

        if (teams == null) {

            teams = new HashSet<>();
        }

        return teams;
    }

    /**
     * Sets the teams.
     *
     * @param teams the teams
     */
    public void setTeams(final Set<String> teams) {

        this.teams = teams;
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

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((crowdProjectId == null) ? 0 : crowdProjectId.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((organization == null) ? 0 : organization.hashCode());
        result = prime * result + ((primaryContactEmail == null) ? 0 : primaryContactEmail.hashCode());
        result = prime * result + (privateProject ? 1231 : 1237);
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
        result = prime * result + ((teams == null) ? 0 : teams.hashCode());
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

        final Project other = (Project) obj;

        if (crowdProjectId == null) {

            if (other.crowdProjectId != null) {

                return false;
            }

        } else if (!crowdProjectId.equals(other.crowdProjectId)) {

            return false;
        }

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

        if (privateProject != other.privateProject) {

            return false;
        }

        if (roles == null) {

            if (other.roles != null) {

                return false;
            }

        } else if (!roles.equals(other.roles)) {

            return false;
        }

        if (teams == null) {

            if (other.teams != null) {

                return false;
            }

        } else if (!teams.equals(other.teams)) {

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

        if (getOrganization() == null) {

            throw new Exception("Unexpected null/empty organization");
        }

        if (StringUtils.isBlank(getName())) {

            throw new Exception("Unexpected null/empty name");
        }

    }

    /* see superclass */
    @Override
    public void validateUpdate(final Project other) throws Exception {

        if (StringUtils.isBlank(getId())) {

            throw new Exception("Unexpected null/empty id");
        }

        if (getOrganization() == null) {

            throw new Exception("Unexpected null/empty organization");
        }

        if (StringUtils.isBlank(getName())) {

            throw new Exception("Unexpected null/empty name");
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
