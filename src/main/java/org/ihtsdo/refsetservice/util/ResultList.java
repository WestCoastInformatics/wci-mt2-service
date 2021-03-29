
package org.ihtsdo.refsetservice.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlTransient;

import org.ihtsdo.refsetservice.model.Collection;
import org.ihtsdo.refsetservice.model.SearchParameters;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a list of results. Aligns with "collection".
 *
 * @param <T> the type parameter
 */
public class ResultList<T> implements Collection<T> {

    /** The total count. */
    private int total = 0;

    /** The objects. */
    private List<T> items = null;

    /** The limit. */
    private int limit;

    /** The offset. */
    private int offset;

    /** The score map. */
    private Map<String, Float> scoreMap = null;
    
    /** The time taken. */
    private Long timeTaken;

    /** The parameters. */
    private SearchParameters parameters = null;

    /**
     * Instantiates an empty {@link ResultList}.
     */
    public ResultList() {
        // n/a
    }

    /**
     * Instantiates a {@link ResultList} from the specified parameters.
     *
     * @param items the items
     */
    public ResultList(final List<T> items) {
        this.items = items;
        if (items != null) {
            this.total = items.size();
        } else {
            this.total = 0;
        }
    }

    /**
     * Size.
     *
     * @return the int
     */
    public int size() {
        return getItems().size();
    }

    /**
     * Contains.
     *
     * @param element the element
     * @return true, if successful
     */
    public boolean contains(final T element) {
        return items.contains(element);
    }

    /**
     * Sets the items.
     *
     * @param items the items
     */
    @Override
    public void setItems(final List<T> items) {
        this.items = items;
    }

    /**
     * Returns the but in an XML transient way.
     *
     * @return the objects transient
     */
    @Override
    @JsonProperty("items")
    public List<T> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    /**
     * Returns the score map.
     *
     * @return the score map
     */
    @XmlTransient
    public Map<String, Float> getScoreMap() {
        if (scoreMap == null) {
            scoreMap = new HashMap<>();
        }
        return scoreMap;
    }

    /**
     * Sets the score map.
     *
     * @param scoreMap the score map
     */
    public void setScoreMap(final Map<String, Float> scoreMap) {
        this.scoreMap = scoreMap;
    }

    /* see superclass */
    @Override
    public int getTotal() {
        return total;
    }

    /* see superclass */
    @Override
    public void setTotal(final int total) {
        this.total = total;
    }

    /* see superclass */
    @Override
    public int getLimit() {
        return limit;
    }

    /* see superclass */
    @Override
    public void setLimit(final int limit) {
        this.limit = limit;
    }

    /* see superclass */
    @Override
    public int getOffset() {
        return offset;
    }

    /* see superclass */
    @Override
    public void setOffset(final int offset) {
        this.offset = offset;
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

    /* see superclass */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((items == null) ? 0 : items.hashCode());
        result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + total;
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
        @SuppressWarnings("unchecked")
        final ResultList<T> other = (ResultList<T>) obj;
        if (items == null) {
            if (other.items != null) {
                return false;
            }
        } else if (!items.equals(other.items)) {
            return false;
        }
        if (parameters == null) {
            if (other.parameters != null) {
                return false;
            }
        } else if (!parameters.equals(other.parameters)) {
            return false;
        }
        if (total != other.total) {
            return false;
        }
        return true;
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
