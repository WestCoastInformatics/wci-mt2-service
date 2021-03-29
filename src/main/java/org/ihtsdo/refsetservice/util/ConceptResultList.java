
package org.ihtsdo.refsetservice.util;

import org.ihtsdo.refsetservice.model.Concept;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a list of concepts.
 */
@Schema(description = "Represents a list of concepts returned from a find call")
public class ConceptResultList extends ResultList<Concept> {
    // n/a - this class exists for API documentation
}
