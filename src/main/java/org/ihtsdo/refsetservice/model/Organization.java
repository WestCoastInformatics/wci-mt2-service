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
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Represents an Organization.
 */
@Entity
@Table(name = "organizations")
@Indexed
public class Organization extends AbstractHasModified {

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The description. */
    @Column(nullable = true, length = 4000)
    private String description;
    
    /** The edition. */
    @ManyToOne(targetEntity = Edition.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Edition edition;

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
    public void populateFrom(final Organization other) {
        super.populateFrom(other);
        name = other.getName();
        description = other.getDescription();
        edition = other.getEdition();
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
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(final String description) {
        this.description = description;
    }
    
    /**
     * Gets the edition.
     *
     * @return the edition
     */
    @JsonSerialize(contentAs = Edition.class)
    @JsonDeserialize(contentAs = Edition.class)
    public Edition getEdition() {
        return edition;
    }

    /**
     * Sets the edition.
     *
     * @param edition the edition to set
     */
    public void setEdition(final Edition edition) {
        this.edition = edition;
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
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((edition == null) ? 0 : edition.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((primaryContactEmail == null) ? 0 : primaryContactEmail.hashCode());
        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
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
        final Organization other = (Organization) obj;
        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }
        if (edition == null) {
            if (other.edition != null) {
                return false;
            }
        } else if (!edition.equals(other.edition)) {
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
        return true;
    }

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
}