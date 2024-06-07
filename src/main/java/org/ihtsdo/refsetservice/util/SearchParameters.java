/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents search parameters for a "find" call.
 */
@JsonInclude(Include.NON_EMPTY)
@Schema(description = "Represents a set of search parameters for finding data.")
public class SearchParameters {

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

	/** Flag for if this search is for editing. */
	private Boolean editing = false;
	
	/** The search after. */
	private String searchAfter;

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

		query = other.getQuery();
		limit = other.getLimit();
		offset = other.getOffset();
		activeOnly = other.getActiveOnly();
		sort = other.getSort();
		sortAscending = other.getSortAscending();
		editing = other.getEditing();
		searchAfter = other.getSearchAfter();
	}

	/**
	 * Returns the query.
	 *
	 * @return the query
	 */
	@Schema(description = "The search query")
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
	@Schema(description = "Indicates the maximum number of search results.")
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
	@Schema(description = "Indicates the start index of the search results")
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
	@Schema(description = "Indicates that only active content should be searched.")
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
	@Schema(description = "Indicates the sort field for search results")
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
	@Schema(description = "Indicates whether sort is ascending (<code>true</code>) "
			+ "or descending (<code>false</code>).")
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
	 * Returns the editing flag.
	 *
	 * @return the editing flag
	 */
	@Schema(description = "Only used when getting members or concepts for members "
			+ "and indicates inferred form should be used an leaf flags included.")
	public Boolean getEditing() {

		return editing;
	}

	/**
	 * Sets the editing flag.
	 *
	 * @param editing the editing flag
	 */
	public void setEditing(final Boolean editing) {

		this.editing = editing;
	}
	
	
    /**
     * Returns the searchAfter.
     *
     * @return the searchAfter
     */
    @Schema(description = "Indicates the searchAfter value for search results")
    public String getSearchAfter() {

      return searchAfter;
    }

    /**
     * Sets the search after.
     *
     * @param searchAfter the new search after
     */
    public void setSearchAfter(final String searchAfter) {

      this.searchAfter = searchAfter;
    }

	/**
	 * Sets the sort ascending.
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

		if (!activeOnly.equals(other.activeOnly)) {
			return false;
		}

		if (!sortAscending.equals(other.sortAscending)) {
			return false;
		}

		if (!editing.equals(other.editing)) {
			return false;
		}
		
		if (!searchAfter.equals(other.searchAfter)) {
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
		result = prime * result + (activeOnly ? 1 : 0);
		result = prime * result + (sortAscending ? 1 : 0);
		result = prime * result + (editing ? 1 : 0);
		result = prime * result + ((searchAfter == null) ? 0 : searchAfter.hashCode());
		return result;
	}

	/**
	 * To string.
	 *
	 * @return the string
	 */
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
