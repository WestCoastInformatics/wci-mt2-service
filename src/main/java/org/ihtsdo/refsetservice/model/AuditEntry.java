/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a project.
 */
@Entity
@Table(name = "audit_entries")
@Schema(description = "Represents an audit entry")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class AuditEntry extends AbstractHasModified {

    /** The message. */
    @Column(nullable = true, length = 255)
    private String message;

    /** The details. */
    @Column(nullable = true, length = 4000)
    private String details;

    /**
     * Instantiates an empty {@link AuditEntry}.
     */
    public AuditEntry() {

        // n/a
    }

    /**
     * Instantiates a {@link AuditEntry} from the specified parameters.
     *
     * @param message the message
     * @param details the details
     */
    public AuditEntry(final String message, final String details) {

        this.message = message;
        this.details = details;
    }

    /**
     * Instantiates a {@link AuditEntry} from the specified parameters.
     *
     * @param other the other
     */
    public AuditEntry(final AuditEntry other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final AuditEntry other) {

        super.populateFrom(other);
        message = other.getMessage();
        details = other.getDetails();

    }

    /**
     * Returns the message.
     *
     * @return the message
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "messageSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getMessage() {

        return message;
    }

    /**
     * Sets the message.
     *
     * @param message the message to set
     */
    public void setMessage(final String message) {

        this.message = message;
    }

    /**
     * Returns the details.
     *
     * @return the details
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    public String getDetails() {

        return details;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((details == null) ? 0 : details.hashCode());
        result = prime * result + ((message == null) ? 0 : message.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AuditEntry other = (AuditEntry) obj;
        if (details == null) {
            if (other.details != null) {
                return false;
            }
        } else if (!details.equals(other.details)) {
            return false;
        }
        if (message == null) {
            if (other.message != null) {
                return false;
            }
        } else if (!message.equals(other.message)) {
            return false;
        }
        return true;
    }

    /**
     * To log string.
     *
     * @return the string
     */
    public String toLogString() {

        return "AUDIT [message=" + message + ", details=" + details + ", modified=" + getModified() + ", modified=" + getModifiedBy() + "]";
    }

    /**
     * Sets the details.
     *
     * @param details the details to set
     */
    public void setDetails(final String details) {

        this.details = details;
    }

    /* see superclass */
    @Override
    public void lazyInit() {

        // TODO Auto-generated method stub

    }

}
