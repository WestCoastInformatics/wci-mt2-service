package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.ihtsdo.refsetservice.util.LanguageUtility;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Branch language-refset metadata used for fullyQualifiedLanguageRefsets.languageLabel.
 */
public class LanguageUtilityBranchMetadataTest {

    @Test
    public void optionalLabelIsGpNynorsk() throws Exception {

        final Map<String, Map<String, String>> meta = parseMetadata("""
            {
              "optionalLanguageRefsets": [
                { "refsetId": "188001000202106", "key": "gpn", "language": "no-nn", "label": "GP Nynorsk" }
              ]
            }
            """);

        assertTrue(meta.containsKey("188001000202106"));
        assertEquals("GP Nynorsk", meta.get("188001000202106").get(LanguageUtility.META_LABEL));
    }

    @Test
    public void cleanedFsnDropsRefsetSuffix() {

        assertEquals("Norwegian Nynorsk language reference set for General Practitioners", LanguageUtility.cleanLanguageRefsetFsnForLabel(
            "Norwegian Nynorsk language reference set for General Practitioners (foundation metadata concept)"));
    }

    private static Map<String, Map<String, String>> parseMetadata(final String json) throws Exception {

        final JsonNode metadata = new ObjectMapper().readTree(json);
        return LanguageUtility.parseBranchLanguageRefsetMetadata(metadata);
    }
}
