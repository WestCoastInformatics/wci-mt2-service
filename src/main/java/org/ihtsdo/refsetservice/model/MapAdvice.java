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

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Class MapAdvice.
 */
@Entity
@Table(name = "map_advices", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapAdvice extends AbstractHasModified {

    /** The name. */
    @Column(nullable = false, unique = true, length = 255)
    private String name;

    /** The detail. */
    @Column(nullable = false, unique = true, length = 255)
    private String detail;

    /** Flag for whether this advice is valid for a null target. */
    @Column(nullable = false)
    private boolean isAllowableForNullTarget;

    /** The is computable. */
    @Column(nullable = false)
    private boolean isComputed;

    /**
     * Instantiates an empty {@link MapAdvice}.
     */
    public MapAdvice() {

        // empty
    }

    /**
     * Instantiates a new map advice.
     *
     * @param mapAdvice the map advice
     */
    public MapAdvice(final MapAdvice mapAdvice) {

        populateFrom(mapAdvice);
    }

    /**
     * Populate from.
     *
     * @param mapAdvice the map advice
     */
    public void populateFrom(final MapAdvice mapAdvice) {

        super.populateFrom(mapAdvice);
        this.name = mapAdvice.getName();
        this.detail = mapAdvice.getDetail();
        this.isAllowableForNullTarget = mapAdvice.isAllowableForNullTarget();
        this.isComputed = mapAdvice.isComputed();
    }

    /**
     * Gets the detail.
     *
     * @return the detail
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getDetail() {

        return detail;
    }

    /**
     * Sets the detail.
     *
     * @param detail the new detail
     */
    public void setDetail(final String detail) {

        this.detail = detail;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getName() {

        return name;
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
     * Checks if is allowable for null target.
     *
     * @return true, if is allowable for null target
     */
    public boolean isAllowableForNullTarget() {

        return isAllowableForNullTarget;
    }

    /**
     * Sets the allowable for null target.
     *
     * @param isAllowableForNullTarget the new allowable for null target
     */
    public void setAllowableForNullTarget(final boolean isAllowableForNullTarget) {

        this.isAllowableForNullTarget = isAllowableForNullTarget;
    }

    /**
     * Checks if is computed.
     *
     * @return true, if is computed
     */
    public boolean isComputed() {

        return isComputed;
    }

    /**
     * Sets the computed.
     *
     * @param isComputed the new computed
     */
    public void setComputed(final boolean isComputed) {

        this.isComputed = isComputed;
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
        result = prime * result + Objects.hash(detail, isAllowableForNullTarget, isComputed, name);
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

        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        MapAdvice other = (MapAdvice) obj;
        return Objects.equals(detail, other.detail) && isAllowableForNullTarget == other.isAllowableForNullTarget && isComputed == other.isComputed
            && Objects.equals(name, other.name);
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

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }

}
