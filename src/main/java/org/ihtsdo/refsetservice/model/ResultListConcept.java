
package org.ihtsdo.refsetservice.model;

import java.util.List;

import org.ihtsdo.refsetservice.util.ResultList;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a list of concepts.
 */
@Schema(description = "Represents a list of concepts returned from a find call")
public class ResultListConcept extends ResultList<Concept> {

    /**
     * Instantiates an empty {@link ResultListConcept}.
     */
    public ResultListConcept() {

        // NA
    }

    /**
     * Instantiates a {@link ResultListConcept} from the specified parameters.
     *
     * @param items the items
     */
    public ResultListConcept(final List<Concept> items) {

        super(items);
    }

    /**
     * Instantiates a {@link ResultListConcept} from the specified parameters.
     *
     * @param other the other
     */
    public ResultListConcept(final ResultListConcept other) {

        super.populateFrom(other);
    }
}
