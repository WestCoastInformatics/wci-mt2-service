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

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;

/**
 * Represents the edition information for a refset.
 */
@Entity
@Table(name = "editions")
@Indexed
public class Edition extends AbstractHasModified {

    /** The code. */
    @Column(nullable = false)
    private String code;

    /** The name. */
    @Column(nullable = false)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    private String name;

    /** The country. */
    @Column(nullable = false)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    private String country;

    /** The flag icon URI. */
    @Column(nullable = true)
    private String iconUri;

    /** The branch to use when retrieving from a terminology server. */
    @Column(nullable = true)
    private String branch;

    /** The description. */
    @Column(nullable = true, length = 4000)
    private String description;

    /**
     * Instantiates an empty {@link Edition}.
     */
    public Edition() {
        // n/a
    }

    /**
     * Instantiates a {@link Edition} from the specified parameters.
     *
     * @param other the other
     */
    public Edition(final Edition other) {
        populateFrom(other);
    }

    /**
     * Instantiates a {@link Edition} from the specified parameters.
     *
     * @param code the key
     * @param name the value
     */
    public Edition(final String code, final String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Edition other) {
        super.populateFrom(other);
        code = other.getCode();
        name = other.getName();
        description = other.getDescription();
        branch = other.getBranch();
        iconUri = other.getIconUri();
        country = other.getCountry();
    }

    /**
     * Returns the code.
     *
     * @return the code
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the code.
     *
     * @param code the code
     */
    public void setCode(final String code) {
        this.code = code;
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
     * @return the iconUri
     */
    public String getIconUri() {
        return iconUri;
    }

    /**
     * @param iconUri the iconUri to set
     */
    public void setIconUri(String iconUri) {
        this.iconUri = iconUri;
    }

    /**
     * @return the branch
     */
    public String getBranch() {
        return branch;
    }

    /**
     * @param branch the branch to set
     */
    public void setBranch(String branch) {
        this.branch = branch;
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
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the country
     */
    public String getCountry() {
        return country;
    }

    /**
     * @param country the country to set
     */
    public void setCountry(String country) {
        this.country = country;
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
        result = prime * result + ((code == null) ? 0 : code.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((branch == null) ? 0 : branch.hashCode());
        result = prime * result + ((iconUri == null) ? 0 : iconUri.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((country == null) ? 0 : country.hashCode());
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

        final Edition other = (Edition) obj;

        if (code == null) {
            if (other.code != null) {
                return false;
            }
        } else if (!code.equals(other.code)) {
            return false;
        }

        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }

        if (branch == null) {
            if (other.branch != null) {
                return false;
            }
        } else if (!branch.equals(other.branch)) {
            return false;
        }

        if (iconUri == null) {
            if (other.iconUri != null) {
                return false;
            }
        } else if (!iconUri.equals(other.iconUri)) {
            return false;
        }

        if (description == null) {
            if (other.description != null) {
                return false;
            }
        } else if (!description.equals(other.description)) {
            return false;
        }

        if (country == null) {
            if (other.country != null) {
                return false;
            }
        } else if (!country.equals(other.country)) {
            return false;
        }

        return true;
    }

    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
