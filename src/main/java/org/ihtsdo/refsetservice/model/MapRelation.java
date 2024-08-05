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
 * The Class MapRelation.
 */
@Entity
@Table(name = "map_relations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class MapRelation extends AbstractHasModified {

    /** The terminology id. */
    @Column(nullable = false)
    private String terminologyId;

    /** The name. */
    @Column(nullable = false)
    private String name;

    /** The abbreviation for display. */
    @Column(nullable = true)
    private String abbreviation;

    /** Whether this relation can be used for null targets. */
    @Column(nullable = false)
    private boolean isAllowableForNullTarget;

    /** Whether this relation is computed. */
    @Column(nullable = false)
    private boolean isComputed;

    /**
     * Instantiates a new map relation.
     */
    public MapRelation() {

        // do nothing
    }

    /**
     * Instantiates a new map relation.
     *
     * @param mapRelation the map relation
     */
    public MapRelation(final MapRelation mapRelation) {

        populateFrom(mapRelation);
    }

    /**
     * Populate from.
     *
     * @param mapRelation the map relation
     */
    public void populateFrom(final MapRelation mapRelation) {

        super.populateFrom(mapRelation);
        this.terminologyId = mapRelation.getTerminologyId();
        this.name = mapRelation.getName();
        this.abbreviation = mapRelation.getAbbreviation();
        this.isAllowableForNullTarget = mapRelation.isAllowableForNullTarget();
        this.isComputed = mapRelation.isComputed();
    }

    /**
     * Gets the terminology id.
     *
     * @return the terminology id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getTerminologyId() {

        return terminologyId;
    }

    /**
     * Sets the terminology id.
     *
     * @param terminologyId the new terminology id
     */
    public void setTerminologyId(final String terminologyId) {

        this.terminologyId = terminologyId;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
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
     * Gets the abbreviation.
     *
     * @return the abbreviation
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getAbbreviation() {

        return abbreviation;
    }

    /**
     * Sets the abbreviation.
     *
     * @param abbreviation the new abbreviation
     */
    public void setAbbreviation(final String abbreviation) {

        this.abbreviation = abbreviation;
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
        result = prime * result + Objects.hash(abbreviation, isAllowableForNullTarget, isComputed, name, terminologyId);
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
        MapRelation other = (MapRelation) obj;
        return Objects.equals(abbreviation, other.abbreviation) && isAllowableForNullTarget == other.isAllowableForNullTarget && isComputed == other.isComputed
            && Objects.equals(name, other.name) && Objects.equals(terminologyId, other.terminologyId);
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
