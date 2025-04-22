/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
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
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;

/**
 * Represents the changes of workflow state for a refset version.
 */
@Entity
@Table(name = "refset_workflow_history")
@Indexed
public class RefsetWorkflowHistory extends AbstractHasModified {

    /** The username. */
    @Column(nullable = false, length = 256)
    private String userName;

    /** The workflow status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 256)
    private WorkflowStatus workflowStatus;

    /** The workflow status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 256)
    private WorkflowAction workflowAction;

    /** The workflow status notes. */
    @Column(nullable = true, length = 10000)
    @Type(type = "text")
    private String notes;

    /** The refset version. */
    @ManyToOne(targetEntity = Refset.class)
    @JoinColumn(nullable = false)
    @Fetch(FetchMode.JOIN)
    private Refset refset;

    /**
     * Instantiates an empty {@link RefsetWorkflowHistory}.
     */
    public RefsetWorkflowHistory() {

        // n/a
    }

    /**
     * Instantiates a {@link RefsetWorkflowHistory} from the specified parameters.
     *
     * @param other the other
     */
    public RefsetWorkflowHistory(final RefsetWorkflowHistory other) {

        populateFrom(other);
    }

    /**
     * Instantiates a {@link RefsetWorkflowHistory} from the specified parameters.
     *
     * @param userName the username
     * @param workflowStatus the workflow status
     * @param workflowAction the workflow action
     * @param notes the notes
     * @param refset the refset
     */
    public RefsetWorkflowHistory(final String userName, final WorkflowStatus workflowStatus, final WorkflowAction workflowAction, final String notes,
        final Refset refset) {

        this.userName = userName;
        this.workflowStatus = workflowStatus;
        this.workflowAction = workflowAction;
        this.notes = notes;
        this.refset = refset;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final RefsetWorkflowHistory other) {

        super.populateFrom(other);
        userName = other.getUserName();
        workflowStatus = other.getWorkflowStatus();
        workflowAction = other.getWorkflowAction();
        notes = other.getNotes();
        refset = other.getRefset();
    }

    /**
     * Returns the userName.
     *
     * @return the userName
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "userNameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getUserName() {

        return userName;
    }

    /**
     * Sets the userName.
     *
     * @param userName the userName
     */
    public void setUserName(final String userName) {

        this.userName = userName;
    }

    /**
     * Gets the notes.
     *
     * @return the notes
     */
    public String getNotes() {

        return notes;
    }

    /**
     * Sets the notes.
     *
     * @param notes the notes to set
     */
    public void setNotes(final String notes) {

        this.notes = notes;
    }

    /**
     * Returns the workflow status.
     *
     * @return the workflow status
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "workflowStatusSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public WorkflowStatus getWorkflowStatus() {

        return workflowStatus;
    }

    /**
     * Sets the workflow status.
     *
     * @param workflowStatus the workflow status
     */
    public void setWorkflowStatus(final WorkflowStatus workflowStatus) {

        this.workflowStatus = workflowStatus;
    }

    /**
     * Returns the workflow action.
     *
     * @return the workflow action
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "workflowActionSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public WorkflowAction getWorkflowAction() {

        return workflowAction;
    }

    /**
     * Sets the workflow action.
     *
     * @param workflowAction the workflow action
     */
    public void setWorkflowAction(final WorkflowAction workflowAction) {

        this.workflowAction = workflowAction;
    }

    /**
     * Gets the refset.
     *
     * @return the refset
     */
    public Refset getRefset() {

        return refset;
    }

    /**
     * Sets the refset.
     *
     * @param refset the refset to set
     */
    public void setRefset(final Refset refset) {

        this.refset = refset;
    }

    /**
     * Returns the refset ID.
     *
     * @return the workflow status
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "refset")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getRefsetId() {

        return refset == null ? null : refset.getId();
    }

    /**
     * Sets the refsetId.
     *
     * @param refsetId the refset id
     */
    public void setRefsetId(final String refsetId) {

        // NA
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

        final RefsetWorkflowHistory other = (RefsetWorkflowHistory) obj;

        if (userName == null) {
            if (other.userName != null) {
                return false;
            }
        } else if (!userName.equals(other.userName)) {
            return false;
        }

        if (notes == null) {
            if (other.notes != null) {
                return false;
            }
        } else if (!notes.equals(other.notes)) {
            return false;
        }

        if (workflowStatus == null) {
            if (other.workflowStatus != null) {
                return false;
            }
        } else if (!workflowStatus.equals(other.workflowStatus)) {
            return false;
        }

        if (workflowAction == null) {
            if (other.workflowAction != null) {
                return false;
            }
        } else if (!workflowAction.equals(other.workflowAction)) {
            return false;
        }

        if (refset == null) {
            if (other.refset != null) {
                return false;
            }
        } else if (!refset.equals(other.refset)) {
            return false;
        }

        return true;
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
        result = prime * result + ((userName == null) ? 0 : userName.hashCode());
        result = prime * result + ((notes == null) ? 0 : notes.hashCode());
        result = prime * result + ((workflowStatus == null) ? 0 : workflowStatus.hashCode());
        result = prime * result + ((workflowAction == null) ? 0 : workflowAction.hashCode());
        result = prime * result + ((refset == null) ? 0 : refset.hashCode());

        return result;
    }

    /* see superclass */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
