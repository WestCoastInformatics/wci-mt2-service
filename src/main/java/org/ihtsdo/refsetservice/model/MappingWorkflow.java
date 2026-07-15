/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

/**
 * Per-source-concept mapping workflow state for a mapset version.
 */
@Entity
@Table(name = "mapping_workflow", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "mapSet_id", "sourceConceptCode", "specialistSlot"
    })
})
@Indexed
public class MappingWorkflow extends AbstractHasModified {

    /** The source concept code. */
    @Column(nullable = false, length = 255)
    private String sourceConceptCode;

    /** The workflow phase. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 256)
    private MapWorkflowStatus workflowStatus;

    /** The assigned user. */
    @Column(nullable = true, length = 256)
    private String assignedUser;

    /** When the mapping was assigned. */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date assignedAt;

    /** When the assignment lease expires. */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date leaseExpiresAt;

    /** Specialist slot (1 or 2 for conflict workflows). */
    @Column(nullable = false)
    private int specialistSlot = 1;

    /** The map set. */
    @ManyToOne(targetEntity = MapSet.class)
    @JoinColumn(name = "mapSet_id", nullable = false)
    @Fetch(FetchMode.JOIN)
    private MapSet mapSet;

    /** The map project. */
    @ManyToOne(targetEntity = MapProject.class)
    @JoinColumn(name = "map_project_id", nullable = true)
    @Fetch(FetchMode.JOIN)
    private MapProject mapProject;

    /**
     * Instantiates an empty {@link MappingWorkflow}.
     */
    public MappingWorkflow() {

        // n/a
    }

    /**
     * Returns the source concept code.
     *
     * @return the source concept code
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getSourceConceptCode() {

        return sourceConceptCode;
    }

    /**
     * Sets the source concept code.
     *
     * @param sourceConceptCode the source concept code
     */
    public void setSourceConceptCode(final String sourceConceptCode) {

        this.sourceConceptCode = sourceConceptCode;
    }

    /**
     * Returns the workflow status.
     *
     * @return the workflow status
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public MapWorkflowStatus getWorkflowStatus() {

        return workflowStatus;
    }

    /**
     * Sets the workflow status.
     *
     * @param workflowStatus the workflow status
     */
    public void setWorkflowStatus(final MapWorkflowStatus workflowStatus) {

        this.workflowStatus = workflowStatus;
    }

    /**
     * Returns the assigned user.
     *
     * @return the assigned user
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getAssignedUser() {

        return assignedUser;
    }

    /**
     * Sets the assigned user.
     *
     * @param assignedUser the assigned user
     */
    public void setAssignedUser(final String assignedUser) {

        this.assignedUser = assignedUser;
    }

    /**
     * Returns the assigned at timestamp.
     *
     * @return the assigned at timestamp
     */
    public Date getAssignedAt() {

        return assignedAt;
    }

    /**
     * Sets the assigned at timestamp.
     *
     * @param assignedAt the assigned at timestamp
     */
    public void setAssignedAt(final Date assignedAt) {

        this.assignedAt = assignedAt;
    }

    /**
     * Returns the lease expires at timestamp.
     *
     * @return the lease expires at timestamp
     */
    public Date getLeaseExpiresAt() {

        return leaseExpiresAt;
    }

    /**
     * Sets the lease expires at timestamp.
     *
     * @param leaseExpiresAt the lease expires at timestamp
     */
    public void setLeaseExpiresAt(final Date leaseExpiresAt) {

        this.leaseExpiresAt = leaseExpiresAt;
    }

    /**
     * Returns the specialist slot.
     *
     * @return the specialist slot
     */
    public int getSpecialistSlot() {

        return specialistSlot;
    }

    /**
     * Sets the specialist slot.
     *
     * @param specialistSlot the specialist slot
     */
    public void setSpecialistSlot(final int specialistSlot) {

        this.specialistSlot = specialistSlot;
    }

    /**
     * Returns the map set.
     *
     * @return the map set
     */
    public MapSet getMapSet() {

        return mapSet;
    }

    /**
     * Sets the map set.
     *
     * @param mapSet the map set
     */
    public void setMapSet(final MapSet mapSet) {

        this.mapSet = mapSet;
    }

    /**
     * Returns the map project.
     *
     * @return the map project
     */
    public MapProject getMapProject() {

        return mapProject;
    }

    /**
     * Sets the map project.
     *
     * @param mapProject the map project
     */
    public void setMapProject(final MapProject mapProject) {

        this.mapProject = mapProject;
    }

    /**
     * Returns the map set id.
     *
     * @return the map set id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "mapSet")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getMapSetId() {

        return mapSet == null ? null : mapSet.getId();
    }

    /**
     * Sets the map set id.
     *
     * @param mapSetId the map set id
     */
    public void setMapSetId(final String mapSetId) {

        // n/a
    }

    /**
     * Returns the map project id.
     *
     * @return the map project id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "mapProject")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getMapProjectId() {

        return mapProject == null ? null : mapProject.getId();
    }

    /**
     * Sets the map project id.
     *
     * @param mapProjectId the map project id
     */
    public void setMapProjectId(final String mapProjectId) {

        // n/a
    }

    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MappingWorkflow other = (MappingWorkflow) obj;
        return specialistSlot == other.specialistSlot
            && Objects.equals(sourceConceptCode, other.sourceConceptCode)
            && workflowStatus == other.workflowStatus
            && Objects.equals(assignedUser, other.assignedUser)
            && Objects.equals(assignedAt, other.assignedAt)
            && Objects.equals(leaseExpiresAt, other.leaseExpiresAt)
            && Objects.equals(mapSet, other.mapSet)
            && Objects.equals(mapProject, other.mapProject);
    }

    @Override
    public int hashCode() {

        return Objects.hash(sourceConceptCode, workflowStatus, assignedUser, assignedAt, leaseExpiresAt, specialistSlot, mapSet, mapProject);
    }

    @Override
    public void lazyInit() {

        // n/a
    }
}
