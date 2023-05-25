
package org.ihtsdo.refsetservice.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The JAXB enabled class for tracking string query params.
 */
public class QueryParameter {

    /** The query. */
    private String query;

    /** The fielded clauses. */
    private Map<String, String> fieldedClauses;

    /** The additional clauses. */
    private Set<String> additionalClauses;

    /**
     * The default constructor.
     */
    public QueryParameter() {

        // do nothing
    }

    /**
     * Instantiates a {@link QueryParameter} from the specified parameters.
     *
     * @param query the query
     */
    public QueryParameter(final String query) {

        this.query = query;
    }

    /**
     * Instantiates a {@link QueryParameter} from the specified parameters.
     *
     * @param query the query
     * @param fieldedClauses the fielded clauses
     * @param additionalClauses the additional clauses
     */
    public QueryParameter(final String query, final Map<String, String> fieldedClauses, final Set<String> additionalClauses) {

        this.query = query;
        this.fieldedClauses = fieldedClauses;
        this.additionalClauses = additionalClauses;
    }

    /**
     * Instantiates a {@link QueryParameter} from the specified parameters.
     *
     * @param qp the qp
     */
    public QueryParameter(final QueryParameter qp) {

        query = qp.getQuery();
        fieldedClauses = new HashMap<>(qp.getFieldedClauses());
        additionalClauses = new HashSet<>(qp.getAdditionalClauses());
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
     * Returns the fielded clauses.
     *
     * @return the fielded clauses
     */
    public Map<String, String> getFieldedClauses() {

        if (fieldedClauses == null) {
            fieldedClauses = new HashMap<>();
        }
        return fieldedClauses;
    }

    /**
     * Sets the fielded clauses.
     *
     * @param fieldedClauses the fielded clauses
     */
    public void setFieldedClauses(final Map<String, String> fieldedClauses) {

        this.fieldedClauses = fieldedClauses;
    }

    /**
     * Returns the additional clauses.
     *
     * @return the additional clauses
     */
    public Set<String> getAdditionalClauses() {

        if (additionalClauses == null) {
            additionalClauses = new HashSet<>();
        }
        return additionalClauses;
    }

    /**
     * Sets the additional clauses.
     *
     * @param additionalClauses the additional clauses
     */
    public void setAdditionalClauses(final Set<String> additionalClauses) {

        this.additionalClauses = additionalClauses;
    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((additionalClauses == null) ? 0 : additionalClauses.hashCode());
        result = prime * result + ((fieldedClauses == null) ? 0 : fieldedClauses.hashCode());
        result = prime * result + ((query == null) ? 0 : query.hashCode());
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
        final QueryParameter other = (QueryParameter) obj;
        if (additionalClauses == null) {
            if (other.additionalClauses != null) {
                return false;
            }
        } else if (!additionalClauses.equals(other.additionalClauses)) {
            return false;
        }
        if (fieldedClauses == null) {
            if (other.fieldedClauses != null) {
                return false;
            }
        } else if (!fieldedClauses.equals(other.fieldedClauses)) {
            return false;
        }
        if (query == null) {
            if (other.query != null) {
                return false;
            }
        } else if (!query.equals(other.query)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "[query=" + query + ", fieldedClauses=" + fieldedClauses + ", additionalClauses=" + additionalClauses + "]";
    }

}
