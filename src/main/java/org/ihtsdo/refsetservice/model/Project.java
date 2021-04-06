/*
 * Copyright 2021 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;

// TODO: Auto-generated Javadoc
/**
 * Represents a project.
 */
@Entity
@Table(name = "projects")
@Indexed
public class Project extends AbstractHasModified {

    /** The name. */
    @Column(nullable = false)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    private String name;

    /** The description. */
    @Column(nullable = true, length = 4000)
    private String description;

    /** The owning organization. */
    @ManyToOne(targetEntity = Organization.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Organization organization;

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
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
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

        return true;
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
