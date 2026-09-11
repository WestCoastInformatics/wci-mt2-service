package org.ihtsdo.refsetservice.handler.snowstorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tests the dummy concept-search used to warm Snowstorm's ECL results cache after map saves.
 */
public class SnowstormMappingEclCacheWarmupTest {

    @Test
    public void warmupRequestMatchesFilteredSearchCacheKey() throws Exception {

        final JsonNode body = ThreadLocalMapper.get().readTree(SnowstormMapping.eclCacheWarmupRequestBody("447562003"));

        assertEquals(SnowstormMapping.ECL_CACHE_WARMUP_TERM, body.get("termFilter").asText());
        assertEquals("^447562003", body.get("eclFilter").asText());
        assertEquals(1, body.get("limit").asInt());
        assertTrue(body.get("termActive").asBoolean());
        assertTrue(body.get("returnIdOnly").asBoolean());
        assertFalse(body.has("activeFilter"));
        assertFalse(body.has("statedEclFilter"));
    }

    @Test
    public void skipsWarmupWhenBranchOrMapSetIsBlank() {

        SnowstormMapping.warmEclResultsCacheAsync(null, "447562003");
        SnowstormMapping.warmEclResultsCacheAsync("MAIN/SNOMEDCT-NO", " ");
        SnowstormMapping.warmEclResultsCacheAsync("", "447562003");
    }
}
