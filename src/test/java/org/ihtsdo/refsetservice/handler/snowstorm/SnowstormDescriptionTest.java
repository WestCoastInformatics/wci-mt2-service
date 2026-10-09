package org.ihtsdo.refsetservice.handler.snowstorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Description;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Mapping descriptions include every active description and language refset.
 */
public class SnowstormDescriptionTest {

    @Test
    public void includesEveryLanguageRefset() {

        final ObjectNode bokmal = description("3884101000202117", "forstyrret frakturtilheling i legg", "no");
        bokmal.putObject("acceptabilityMap").put("61000202103", "PREFERRED");

        final ObjectNode nynorsk = description("6719851000202116", "forstyrra frakturtilheiling i legg", "no");
        nynorsk.putObject("acceptabilityMap").put("91000202106", "ACCEPTABLE");

        final ObjectNode both = description("17462014", "Radiating chest pain", "en");
        final ObjectNode acceptability = both.putObject("acceptabilityMap");
        acceptability.put("900000000000509007", "PREFERRED");
        acceptability.put("900000000000508004", "ACCEPTABLE");

        final List<Description> descriptions = SnowstormDescription.populateDescriptions(Set.of(bokmal, nynorsk, both));
        final Set<String> keys = descriptions.stream().map(description -> description.getLanguageCode() + "|" + description.getTerm())
            .collect(Collectors.toSet());

        assertEquals(4, descriptions.size());
        assertTrue(keys.contains("61000202103|forstyrret frakturtilheling i legg"));
        assertTrue(keys.contains("91000202106|forstyrra frakturtilheiling i legg"));
        assertTrue(keys.contains("900000000000509007|Radiating chest pain"));
        assertTrue(keys.contains("900000000000508004|Radiating chest pain"));
        assertEquals("PT", descriptions.stream().filter(description -> "61000202103".equals(description.getLanguageCode())).findFirst().orElseThrow()
            .getTypeName());
        assertEquals("AC", descriptions.stream().filter(description -> "91000202106".equals(description.getLanguageCode())).findFirst().orElseThrow()
            .getTypeName());
    }

    private static ObjectNode description(final String descriptionId, final String term, final String lang) {

        final ObjectNode node = ThreadLocalMapper.get().createObjectNode();
        node.put("active", true);
        node.put("descriptionId", descriptionId);
        node.put("term", term);
        node.put("lang", lang);
        node.put("type", "SYNONYM");
        node.put("conceptId", "128001000202100");
        return node;
    }
}
