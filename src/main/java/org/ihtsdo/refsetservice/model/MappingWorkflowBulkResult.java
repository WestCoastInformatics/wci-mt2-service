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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregate result for a bulk mapping workflow action.
 */
@JsonInclude(Include.NON_NULL)
@Schema(description = "Bulk mapping workflow status result with per-concept outcomes.")
public class MappingWorkflowBulkResult {

    /** Per-concept outcomes in request order. */
    private List<MappingWorkflowBulkItemResult> items = new ArrayList<>();

    /** Count of successful items. */
    private int successCount;

    /** Count of failed items. */
    private int failureCount;

    /**
     * Instantiates an empty {@link MappingWorkflowBulkResult}.
     */
    public MappingWorkflowBulkResult() {

        // n/a
    }

    /**
     * Returns the items.
     *
     * @return the items
     */
    public List<MappingWorkflowBulkItemResult> getItems() {

        return items;
    }

    /**
     * Sets the items.
     *
     * @param items the items
     */
    public void setItems(final List<MappingWorkflowBulkItemResult> items) {

        this.items = items == null ? new ArrayList<>() : items;
    }

    /**
     * Returns the success count.
     *
     * @return the success count
     */
    public int getSuccessCount() {

        return successCount;
    }

    /**
     * Sets the success count.
     *
     * @param successCount the success count
     */
    public void setSuccessCount(final int successCount) {

        this.successCount = successCount;
    }

    /**
     * Returns the failure count.
     *
     * @return the failure count
     */
    public int getFailureCount() {

        return failureCount;
    }

    /**
     * Sets the failure count.
     *
     * @param failureCount the failure count
     */
    public void setFailureCount(final int failureCount) {

        this.failureCount = failureCount;
    }

    /**
     * Adds an item and updates counters.
     *
     * @param item the item
     */
    public void addItem(final MappingWorkflowBulkItemResult item) {

        items.add(item);
        if (item != null && item.isSuccess()) {
            successCount++;
        } else {
            failureCount++;
        }
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
        final MappingWorkflowBulkResult other = (MappingWorkflowBulkResult) obj;
        return successCount == other.successCount && failureCount == other.failureCount && Objects.equals(items, other.items);
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        return Objects.hash(items, successCount, failureCount);
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
