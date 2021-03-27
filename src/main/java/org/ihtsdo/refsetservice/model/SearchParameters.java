
package org.ihtsdo.refsetservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents search parameters for a "find" call.
 */
@JsonInclude(Include.NON_EMPTY)
public class SearchParameters extends AbstractHasId {

    /** The terminology. */
    private String terminology;

    /** The query. */
    private String query;

    /** The limit. */
    private Integer limit;

    /** The offset. */
    private Integer offset;

    /** The active only. */
    private Boolean activeOnly;

    /** The sort. */
    private String sort;

    /** The sort ascending. */
    private Boolean sortAscending;

    /**
     * Instantiates an empty {@link SearchParameters}.
     */
    public SearchParameters() {
        // n/a
    }

    /**
     * Instantiates a {@link SearchParameters} from the specified parameters.
     *
     * @param other the other
     */
    public SearchParameters(final SearchParameters other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final SearchParameters other) {
        terminology = other.getTerminology();
        query = other.getQuery();
        limit = other.getLimit();
        offset = other.getOffset();
        activeOnly = other.getActiveOnly();
        sort = other.getSort();
        sortAscending = other.getSortAscending();
    }

    /**
     * Returns the terminology.
     *
     * @return the terminology
     */
    public String getTerminology() {
        return terminology;
    }

    /**
     * Sets the terminology.
     *
     * @param terminology the terminology
     */
    public void setTerminology(final String terminology) {
        this.terminology = terminology;
    }

    /**
     * Returns the query.
     *
     * @return the query
     */
    public String getQuery() {
        return query;
    }

    /**
     * Sets the query.
     *
     * @param query the query
     */
    public void setQuery(final String query) {
        this.query = query;
    }

    /**
     * Returns the limit.
     *
     * @return the limit
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Sets the limit.
     *
     * @param limit the limit
     */
    public void setLimit(final Integer limit) {
        this.limit = limit;
    }

    /**
     * Returns the offset.
     *
     * @return the offset
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Sets the offset.
     *
     * @param offset the offset
     */
    public void setOffset(final Integer offset) {
        this.offset = offset;
    }

    /**
     * Returns the active only.
     *
     * @return the active only
     */
    public Boolean getActiveOnly() {
        return activeOnly;
    }

    /**
     * Sets the active only.
     *
     * @param activeOnly the active only
     */
    public void setActiveOnly(final Boolean activeOnly) {
        this.activeOnly = activeOnly;
    }

    /**
     * Returns the sort.
     *
     * @return the sort
     */
    public String getSort() {
        return sort;
    }

    /**
     * Sets the sort.
     *
     * @param sort the sort
     */
    public void setSort(final String sort) {
        this.sort = sort;
    }

    /**
     * Returns the sort ascending.
     *
     * @return the sort ascending
     */
    public Boolean getSortAscending() {
        return sortAscending;
    }

    /**
     * Sets the sort ascending.
     *
     * @param sortAscending the sort ascending
     */
    public void setSortAscending(final Boolean sortAscending) {
        this.sortAscending = sortAscending;
    }

}
