
package org.ihtsdo.refsetservice.model;

import java.util.List;

import org.ihtsdo.refsetservice.util.ResultList;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a list of concepts.
 */
@Schema(description = "Represents a list of concepts returned from a find call")
public class ResultListConceptRef extends ResultList<ConceptRef> {

    /**
     * Instantiates an empty {@link ResultListConceptRef}.
     */
    public ResultListConceptRef() {

        // NA
    }

    /**
     * Instantiates a {@link ResultListConceptRef} from the specified parameters.
     *
     * @param items the items
     */
    public ResultListConceptRef(final List<ConceptRef> items) {

        super(items);
    }

    /**
     * Instantiates a {@link ResultListConceptRef} from the specified parameters.
     *
     * @param other the other
     */
    public ResultListConceptRef(final ResultListConceptRef other) {

        super.populateFrom(other);
    }
}
