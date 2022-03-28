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
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonGetter;

/**
 * Represents a project.
 */
@Entity
@Table(name = "projects")
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
    @Transient
    @Column(nullable = true)
    private String crowdProjectId;

    /** The of teams ids for this project. */
    @ElementCollection
    private Set<String> teams;
    
    /** The of roles for this project. */
    @ElementCollection
    private List<String> roles;

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
        teams = other.getTeams();
    }

    /* see superclass */
    @Override
    public void patchFrom(Project other) {

        super.populateFrom(other);
        name = other.getName();
        organization = other.getOrganization();
        description = other.getDescription();
        privateProject = other.isPrivateProject();
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
    @JsonGetter()
    public Set<String> getTeams() {

        return (teams != null) ? teams : new HashSet<>();
    }

    /**
     * Sets the teams.
     *
     * @param teams the teams
     */
    public void setTeams(Set<String> teams) {

        this.teams = teams;
    }
    
    /**
     * Returns the roles.
     *
     * @return the roles
     */
    @JsonGetter()
    public List<String> getRoles() {

        return (roles != null) ? roles : new ArrayList<>();
    }

    /**
     * Sets the roles.
     *
     * @param teams the roles
     */
    public void setRoles(List<String> roles) {

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
    public void setPrimaryContactEmail(String primaryContactEmail) {

        this.primaryContactEmail = primaryContactEmail;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((organization == null) ? 0 : organization.hashCode());
        result = prime * result + ((teams == null) ? 0 : teams.hashCode());
        result = prime * result + ((roles == null) ? 0 : roles.hashCode());
        result = prime * result + ((primaryContactEmail == null) ? 0 : primaryContactEmail.hashCode());
        result = prime * result + (privateProject ? 1 : 0);
        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
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

        final Project other = (Project) obj;

        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }

        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }

        if (organization == null) {
            if (other.organization != null) {
                return false;
            }
        } else if (!organization.equals(other.organization)) {
            return false;
        }

        if (teams == null) {
            if (other.teams != null) {
                return false;
            }
        } else if (!teams.equals(other.teams)) {
            return false;
        }
        
        if (roles == null) {
            if (other.roles != null) {
                return false;
            }
        } else if (!roles.equals(other.roles)) {
            return false;
        }

        if (privateProject != other.privateProject) {
            return false;
        }

        if (primaryContactEmail == null) {
            if (other.primaryContactEmail != null) {
                return false;
            }
        } else if (!primaryContactEmail.equals(other.primaryContactEmail)) {
            return false;
        }

        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "Project [name=" + name + ", description=" + description + ", organization=" + organization + ", privateProject=" + privateProject + ", primaryContactEmail=" + primaryContactEmail
            + ", crowdProjectId=" + crowdProjectId + ", teams=" + teams + ", roles=" + roles + "]";
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
    public void validateUpdate(AuthContext context, Project other) throws Exception {
        // TODO validate update
    }

    /* see superclass */
    @Override
    public void validateDelete(AuthContext context) throws Exception {
        // TODO valiidate delete
    }
}
