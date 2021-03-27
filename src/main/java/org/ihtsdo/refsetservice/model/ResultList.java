
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a list of results with paging parameters.
 * @param <T> The type of objects results contains.
 */
public class ResultList<T extends HasId> extends AbstractHasId {

    /** The total. */
    private Integer total;

    /** The time taken. */
    private Long timeTaken;

    /** The parameters. */
    private SearchParameters parameters = null;

    /** The results. */
    private List<T> results;

    /**
     * Instantiates an empty {@link ResultList}.
     */
    public ResultList() {
        // n/a
    }

    /**
     * Instantiates a {@link ResultList} from the specified parameters.
     *
     * @param other the other
     */
    public ResultList(final ResultList<T> other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final ResultList<T> other) {
        super.populateFrom(other);
        total = other.getTotal();
        timeTaken = other.getTimeTaken();
        parameters = other.getParameters();
        results = other.getResults();
    }

    /**
     * Returns the results.
     *
     * @return the results
     */
    public List<T> getResults() {

        if (results == null) {
            results = new ArrayList<>();
        }

        return results;
    }

    /**
     * Sets the results.
     *
     * @param results the results
     */
    public void setResults(final List<T> results) {
        this.results = results;
    }

    /**
     * Returns the total.
     *
     * @return the total
     */
    public Integer getTotal() {
        return total;
    }

    /**
     * Sets the total.
     *
     * @param total the total
     */
    public void setTotal(final Integer total) {
        this.total = total;
    }

    /**
     * Time taken.
     *
     * @return the long
     */
    public Long getTimeTaken() {
        return timeTaken;
    }

    /**
     * Sets the time taken.
     *
     * @param timeTaken the time taken
     */
    public void setTimeTaken(final Long timeTaken) {
        this.timeTaken = timeTaken;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters
     */
    public SearchParameters getParameters() {
        return parameters;
    }

    /**
     * Sets the parameters.
     *
     * @param parameters the parameters
     */
    public void setParameters(final SearchParameters parameters) {
        this.parameters = parameters;
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
        result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + ((total == null) ? 0 : total.hashCode());
        result = prime * result + ((this.results == null) ? 0 : this.results.hashCode());
        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @SuppressWarnings("unchecked")
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

        ResultList<T> other;

        try {
            other = (ResultList<T>) obj;
        } catch (Exception e) {
            return false;
        }

        if (parameters == null) {
            if (other.parameters != null) {
                return false;
            }
        } else if (!parameters.equals(other.parameters)) {
            return false;
        }
        if (total == null) {
            if (other.total != null) {
                return false;
            }
        } else if (!total.equals(other.total)) {
            return false;
        }
        if (results == null) {
            if (other.results != null) {
                return false;
            }
        } else if (!results.equals(other.results)) {
            return false;
        }
        return true;
    }

}