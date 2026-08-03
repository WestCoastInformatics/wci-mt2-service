/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Objects;

import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-concept outcome for a bulk mapping workflow action.
 */
@JsonInclude(Include.NON_NULL)
@Schema(description = "Per-concept result for a bulk mapping workflow action.")
public class MappingWorkflowBulkItemResult {

    /** The source concept code. */
    private String conceptCode;

    /** Whether the action succeeded for this concept. */
    private boolean success;

    /** Updated workflow row when successful. */
    private MappingWorkflow workflow;

    /** HTTP-style status for failures (e.g. 401, 400). */
    private Integer status;

    /** Failure message when unsuccessful. */
    private String message;

    /**
     * Instantiates an empty {@link MappingWorkflowBulkItemResult}.
     */
    public MappingWorkflowBulkItemResult() {

        // n/a
    }

    /**
     * Success factory.
     *
     * @param conceptCode the concept code
     * @param workflow the updated workflow
     * @return the item result
     */
    public static MappingWorkflowBulkItemResult success(final String conceptCode, final MappingWorkflow workflow) {

        final MappingWorkflowBulkItemResult result = new MappingWorkflowBulkItemResult();
        result.setConceptCode(conceptCode);
        result.setSuccess(true);
        result.setWorkflow(workflow);
        return result;
    }

    /**
     * Failure factory.
     *
     * @param conceptCode the concept code
     * @param status the status code
     * @param message the message
     * @return the item result
     */
    public static MappingWorkflowBulkItemResult failure(final String conceptCode, final int status, final String message) {

        final MappingWorkflowBulkItemResult result = new MappingWorkflowBulkItemResult();
        result.setConceptCode(conceptCode);
        result.setSuccess(false);
        result.setStatus(status);
        result.setMessage(message);
        return result;
    }

    /**
     * Returns the concept code.
     *
     * @return the concept code
     */
    public String getConceptCode() {

        return conceptCode;
    }

    /**
     * Sets the concept code.
     *
     * @param conceptCode the concept code
     */
    public void setConceptCode(final String conceptCode) {

        this.conceptCode = conceptCode;
    }

    /**
     * Indicates whether the action succeeded.
     *
     * @return true if successful
     */
    public boolean isSuccess() {

        return success;
    }

    /**
     * Sets success.
     *
     * @param success the success flag
     */
    public void setSuccess(final boolean success) {

        this.success = success;
    }

    /**
     * Returns the workflow.
     *
     * @return the workflow
     */
    public MappingWorkflow getWorkflow() {

        return workflow;
    }

    /**
     * Sets the workflow.
     *
     * @param workflow the workflow
     */
    public void setWorkflow(final MappingWorkflow workflow) {

        this.workflow = workflow;
    }

    /**
     * Returns the failure status.
     *
     * @return the status
     */
    public Integer getStatus() {

        return status;
    }

    /**
     * Sets the failure status.
     *
     * @param status the status
     */
    public void setStatus(final Integer status) {

        this.status = status;
    }

    /**
     * Returns the failure message.
     *
     * @return the message
     */
    public String getMessage() {

        return message;
    }

    /**
     * Sets the failure message.
     *
     * @param message the message
     */
    public void setMessage(final String message) {

        this.message = message;
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
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MappingWorkflowBulkItemResult other = (MappingWorkflowBulkItemResult) obj;
        return success == other.success && Objects.equals(conceptCode, other.conceptCode) && Objects.equals(status, other.status)
            && Objects.equals(message, other.message) && Objects.equals(workflow, other.workflow);
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        return Objects.hash(conceptCode, success, workflow, status, message);
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
