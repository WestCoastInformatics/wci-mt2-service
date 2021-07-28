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
import javax.persistence.Table;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

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
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result
                + ((description == null) ? 0 : description.hashCode());
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

        final Organization other = (Organization) obj;

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

        return true;
    }

    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
