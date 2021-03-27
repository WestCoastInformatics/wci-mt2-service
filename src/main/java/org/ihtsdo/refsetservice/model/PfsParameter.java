
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The JAXB enabled implementation of the paging/filtering/sorting object.
 */
public class PfsParameter {

    /** The maximum number of results. */
    private int limit = -1;

    /** The start index for queries. */
    private int offset = 0;

    /** The comparator for sorting. */
    private String sort = null;

    /** The backwards-compatible multiple sort field. */
    private List<String> sortFields = new ArrayList<>();

    /** The ascending flag. */
    private boolean ascending = true;

    /**
     * The default constructor.
     */
    public PfsParameter() {
        // do nothing
    }

    /**
     * Instantiates a {@link PfsParameter} from the specified parameters.
     *
     * @param pfs the pfs
     */
    public PfsParameter(final PfsParameter pfs) {
        limit = pfs.getLimit();
        offset = pfs.getOffset();
        sort = pfs.getSort();
        sortFields = new ArrayList<>(pfs.getSortFields());
        ascending = pfs.isAscending();
    }

    /**
     * Instantiates a {@link PfsParameter} from the specified parameters.
     *
     * @param offset the offset
     * @param limit the limit
     * @param ascending the ascending
     * @param sort the sort
     */
    public PfsParameter(final Integer offset, final Integer limit, final Boolean ascending,
            final String sort) {
        if (offset != null) {
            setOffset(offset);
        }
        if (limit != null) {
            setLimit(limit);
        } else {
            // If not specified, limit to 100 things
            setLimit(100);
        }

        if (ascending != null) {
            setAscending(ascending);
        }
        if (sort != null) {
            setSort(sort);
        }
    }

    /**
     * Instantiates a {@link PfsParameter} from the specified parameters.
     *
     * @param offset the offset
     * @param limit the limit
     */
    public PfsParameter(final int offset, final int limit) {
        this.limit = limit;
        this.offset = offset;
    }

    /**
     * Default instance.
     *
     * @return the pfs parameter
     */
    public static PfsParameter defaultInstance() {
        final PfsParameter pfs = new PfsParameter();
        pfs.setLimit(1000);
        pfs.setOffset(0);
        return pfs;
    }

    /**
     * Returns the max results.
     *
     * @return the max results
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Sets the limit.
     *
     * @param limit the limit
     */

    public void setLimit(final int limit) {
        this.limit = limit;
    }

    /**
     * Returns the offset.
     * 
     * @return the offset
     */

    public int getOffset() {
        return offset;
    }

    /**
     * Sets the offset.
     *
     * @param offset the offset
     */
    public void setOffset(final int offset) {
        this.offset = offset;
    }

    /**
     * Indicates whether or not ascending is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */

    public boolean isAscending() {
        return ascending;
    }

    /**
     * Sets the ascending.
     *
     * @param ascending the ascending
     */

    public void setAscending(final boolean ascending) {
        this.ascending = ascending;
    }

    /**
     * Returns the sort field.
     *
     * @return the sort field
     */

    public String getSort() {
        return sort;
    }

    /**
     * Sets the sort field.
     *
     * @param sort the sort field
     */

    public void setSort(final String sort) {
        this.sort = sort;
    }

    /**
     * Sets the sort fields.
     *
     * @param sortFields the sort fields
     */
    public void setSortFields(final List<String> sortFields) {
        this.sortFields = sortFields;
    }

    /**
     * Returns the sort fields.
     *
     * @return the sort fields
     */

    public List<String> getSortFields() {
        return this.sortFields;
    }

    /* see superclass */

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (ascending ? 1231 : 1237);
        result = prime * result + limit;
        result = prime * result + offset;
        result = prime * result + ((sort == null) ? 0 : sort.hashCode());
        result = prime * result + ((sortFields == null) ? 0 : sortFields.hashCode());
        return result;
    }

    /* see superclass */
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
        final PfsParameter other = (PfsParameter) obj;
        if (ascending != other.ascending) {
            return false;
        }
        if (limit != other.limit) {
            return false;
        }
        if (offset != other.offset) {
            return false;
        }
        if (sort == null) {
            if (other.sort != null) {
                return false;
            }
        } else if (!sort.equals(other.sort)) {
            return false;
        }
        if (sortFields == null) {
            if (other.sortFields != null) {
                return false;
            }
        } else if (!sortFields.equals(other.sortFields)) {
            return false;
        }
        return true;
    }

    /**
     * Indicates whether or not index in range is the case.
     *
     * @param i the i
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isIndexInRange(final int i) {
        return getOffset() != -1 && getLimit() != -1 && i >= getOffset()
                && i < (getOffset() + getLimit());
    }

    /* see superclass */
    @Override
    public String toString() {
        return "PfsParameterJpa [limit=" + limit + ", startIndex=" + offset + ", sort=" + sort
                + ", sortFields=" + sortFields + ", ascending=" + ascending + "]";
    }

}
