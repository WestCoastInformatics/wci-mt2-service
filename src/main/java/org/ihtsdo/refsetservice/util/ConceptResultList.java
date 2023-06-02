
package org.ihtsdo.refsetservice.util;

import java.util.List;

import org.ihtsdo.refsetservice.model.Concept;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a list of concepts.
 */
@Schema(description = "Represents a list of concepts returned from a find call")
public class ConceptResultList extends ResultList<Concept> {

    /**
     * Instantiates an empty {@link ConceptResultList}.
     */
    public ConceptResultList() {

        // NA
    }

    /**
     * Instantiates a {@link ConceptResultList} from the specified parameters.
     *
     * @param items the items
     */
    public ConceptResultList(final List<Concept> items) {

        super(items);
    }

    /**
     * Instantiates a {@link ConceptResultList} from the specified parameters.
     *
     * @param other the other
     */
    public ConceptResultList(final ConceptResultList other) {

        super.populateFrom(other);
    }
}
