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

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;

/**
 * Audit history for per-concept mapping workflow transitions.
 */
@Entity
@Table(name = "mapping_workflow_history")
@Indexed
public class MappingWorkflowHistory extends AbstractHasModified {

    /** The username. */
    @Column(nullable = false, length = 256)
    private String userName;

    /** The workflow status after the transition. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 256)
    private MapWorkflowStatus workflowStatus;

    /** The workflow action taken. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 256)
    private MappingWorkflowAction workflowAction;

    /** Transition notes. */
    @Column(nullable = true, length = 10000)
    @Type(type = "text")
    private String notes;

    /** The mapping workflow row. */
    @ManyToOne(targetEntity = MappingWorkflow.class)
    @JoinColumn(name = "mapping_workflow_id", nullable = false)
    @Fetch(FetchMode.JOIN)
    private MappingWorkflow mappingWorkflow;

    /**
     * Instantiates an empty {@link MappingWorkflowHistory}.
     */
    public MappingWorkflowHistory() {

        // n/a
    }

    /**
     * Returns the user name.
     *
     * @return the user name
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getUserName() {

        return userName;
    }

    /**
     * Sets the user name.
     *
     * @param userName the user name
     */
    public void setUserName(final String userName) {

        this.userName = userName;
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
     * Returns the workflow action.
     *
     * @return the workflow action
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public MappingWorkflowAction getWorkflowAction() {

        return workflowAction;
    }

    /**
     * Sets the workflow action.
     *
     * @param workflowAction the workflow action
     */
    public void setWorkflowAction(final MappingWorkflowAction workflowAction) {

        this.workflowAction = workflowAction;
    }

    /**
     * Returns the notes.
     *
     * @return the notes
     */
    public String getNotes() {

        return notes;
    }

    /**
     * Sets the notes.
     *
     * @param notes the notes
     */
    public void setNotes(final String notes) {

        this.notes = notes;
    }

    /**
     * Returns the mapping workflow.
     *
     * @return the mapping workflow
     */
    public MappingWorkflow getMappingWorkflow() {

        return mappingWorkflow;
    }

    /**
     * Sets the mapping workflow.
     *
     * @param mappingWorkflow the mapping workflow
     */
    public void setMappingWorkflow(final MappingWorkflow mappingWorkflow) {

        this.mappingWorkflow = mappingWorkflow;
    }

    /**
     * Returns the mapping workflow id.
     *
     * @return the mapping workflow id
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "mappingWorkflow")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getMappingWorkflowId() {

        return mappingWorkflow == null ? null : mappingWorkflow.getId();
    }

    /**
     * Sets the mapping workflow id.
     *
     * @param mappingWorkflowId the mapping workflow id
     */
    public void setMappingWorkflowId(final String mappingWorkflowId) {

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
        final MappingWorkflowHistory other = (MappingWorkflowHistory) obj;
        return Objects.equals(userName, other.userName)
            && workflowStatus == other.workflowStatus
            && workflowAction == other.workflowAction
            && Objects.equals(notes, other.notes)
            && Objects.equals(mappingWorkflow, other.mappingWorkflow);
    }

    @Override
    public int hashCode() {

        return Objects.hash(userName, workflowStatus, workflowAction, notes, mappingWorkflow);
    }

    @Override
    public void lazyInit() {

        // n/a
    }
}
