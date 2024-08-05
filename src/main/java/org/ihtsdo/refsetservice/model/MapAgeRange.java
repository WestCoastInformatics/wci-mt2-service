/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// TODO: Auto-generated Javadoc
/**
 *
 * 
 * Generically represents an age range for a map rule.
 */
@Entity
@Table(name = "map_age_ranges", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapAgeRange extends AbstractHasId {

    /** The age range preset name. */
    @Column(nullable = false)
    private String name;

    /** The lower bound parameters. */
    @Column(nullable = false)
    private Integer lowerValue;

    /** The lower units. */
    @Column(nullable = false)
    private String lowerUnits;

    /** The lower inclusive. */
    @Column(nullable = false)
    private boolean lowerInclusive;

    /** The upper bound parameters. */
    @Column(nullable = false)
    private Integer upperValue;

    /** The upper units. */
    @Column(nullable = false)
    private String upperUnits;

    /** The upper inclusive. */
    @Column(nullable = false)
    private boolean upperInclusive;

    /**
     * Instantiates a new map age range jpa.
     */
    public MapAgeRange() {

        // do nothing
    }

    /**
     * Instantiates a new map age range jpa.
     *
     * @param id the id
     * @param name the name
     * @param lowerValue the lower value
     * @param lowerUnits the lower units
     * @param lowerInclusive the lower inclusive
     * @param upperValue the upper value
     * @param upperUnits the upper units
     * @param upperInclusive the upper inclusive
     */
    public MapAgeRange(final String id, final String name, final Integer lowerValue, final String lowerUnits, final boolean lowerInclusive,
        final Integer upperValue, final String upperUnits, final boolean upperInclusive) {

        super();
        super.setId(id);
        this.name = name;
        this.lowerValue = lowerValue;
        this.lowerUnits = lowerUnits;
        this.lowerInclusive = lowerInclusive;
        this.upperValue = upperValue;
        this.upperUnits = upperUnits;
        this.upperInclusive = upperInclusive;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {

        return this.name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Gets the lower value.
     *
     * @return the lower value
     */
    public Integer getLowerValue() {

        return this.lowerValue;
    }

    /**
     * Sets the lower value.
     *
     * @param value the new lower value
     */
    public void setLowerValue(final Integer value) {

        this.lowerValue = value;
    }

    /**
     * Gets the lower units.
     *
     * @return the lower units
     */
    public String getLowerUnits() {

        return this.lowerUnits;
    }

    /**
     * Sets the lower units.
     *
     * @param units the new lower units
     */
    public void setLowerUnits(final String units) {

        this.lowerUnits = units;
    }

    /**
     * Gets the lower inclusive.
     *
     * @return the lower inclusive
     */
    public boolean getLowerInclusive() {

        return this.lowerInclusive;
    }

    /**
     * Sets the lower inclusive.
     *
     * @param inclusive the new lower inclusive
     */
    public void setLowerInclusive(final boolean inclusive) {

        this.lowerInclusive = inclusive;

    }

    /**
     * Gets the upper value.
     *
     * @return the upper value
     */
    public Integer getUpperValue() {

        return this.upperValue;
    }

    /**
     * Sets the upper value.
     *
     * @param value the new upper value
     */
    public void setUpperValue(final Integer value) {

        this.upperValue = value;
    }

    /**
     * Gets the upper units.
     *
     * @return the upper units
     */
    public String getUpperUnits() {

        return this.upperUnits;
    }

    /**
     * Sets the upper units.
     *
     * @param units the new upper units
     */
    public void setUpperUnits(final String units) {

        this.upperUnits = units;
    }

    /**
     * Gets the upper inclusive.
     *
     * @return the upper inclusive
     */
    public boolean getUpperInclusive() {

        return this.upperInclusive;
    }

    /**
     * Sets the upper inclusive.
     *
     * @param inclusive the new upper inclusive
     */
    public void setUpperInclusive(final boolean inclusive) {

        this.upperInclusive = inclusive;
    }

    /**
     * Returns <code>true</code> if lowerValue is -1.
     *
     * @return true, if successful
     */

    public boolean hasLowerBound() {

        return this.lowerValue == -1 ? false : true;
    }

    /**
     * Returns <code>true</code> if upperValue is -1.
     *
     * @return true, if successful
     */

    public boolean hasUpperBound() {

        return this.upperValue == -1 ? false : true;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(lowerInclusive, lowerUnits, lowerValue, name, upperInclusive, upperUnits, upperValue);
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

        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        final MapAgeRange other = (MapAgeRange) obj;
        return lowerInclusive == other.lowerInclusive && Objects.equals(lowerUnits, other.lowerUnits) && Objects.equals(lowerValue, other.lowerValue)
            && Objects.equals(name, other.name) && upperInclusive == other.upperInclusive && Objects.equals(upperUnits, other.upperUnits)
            && Objects.equals(upperValue, other.upperValue);
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

}