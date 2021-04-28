
package org.ihtsdo.refsetservice.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents search parameters for a "find" call.
 */
@JsonInclude(Include.NON_EMPTY)
public class SearchParameters {

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
    
    /**
     * Sets the sort ascending.
     *
     * @param obj the obj
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
        
        final SearchParameters other = (SearchParameters) obj;
        
        if (query == null) {
            
            if (other.query != null) {
                return false;
            }
            
        } else if (!query.equals(other.query)) {
            return false;
        }
        
        if (sort == null) {
            
            if (other.sort != null) {
                return false;
            }
            
        } else if (!sort.equals(other.sort)) {
            return false;
        }
        
        if (limit == null) {
            
            if (other.limit != null) {
                return false;
            }
            
        } else if (!limit.equals(other.limit)) {
            return false;
        }
        
        if (offset == null) {
            
            if (other.offset != null) {
                return false;
            }
            
        } else if (!offset.equals(other.offset)) {
            return false;
        }
        
        if (terminology == null) {
            
            if (other.terminology != null) {
                return false;
            }
            
        } else if (!terminology.equals(other.terminology)) {
            return false;
        }
        
        if (activeOnly != other.activeOnly) {
            return false;
        }

        if (sortAscending != other.sortAscending) {
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
        result = prime * result + ((limit == null) ? 0 : limit.hashCode());
        result = prime * result + ((offset == null) ? 0 : offset.hashCode());
        result = prime * result + ((query == null) ? 0 : query.hashCode());
        result = prime * result + ((sort == null) ? 0 : sort.hashCode());
        result = prime * result + ((terminology == null) ? 0 : terminology.hashCode());
        result = prime * result + (activeOnly ? 1 : 0);
        result = prime * result + (sortAscending ? 1 : 0);
        return result;
    }

    /* see superclass */
    @Override
    public String toString() {
        
        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }
}
