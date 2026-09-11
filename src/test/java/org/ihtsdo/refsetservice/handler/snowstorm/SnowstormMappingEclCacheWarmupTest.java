package org.ihtsdo.refsetservice.handler.snowstorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tests ECL cache warmup after map saves and search wait behavior.
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

    @Test
    public void searchReturnsImmediatelyWhenNoWarmupIsInFlight() {

        SnowstormMapping.awaitEclCacheWarmup("MAIN/SNOMEDCT-NO", "447562003", 50L);
    }

    @Test
    public void searchWaitsForWarmupToFinish() {

        final String branch = "MAIN/TEST-WAIT";
        final String mapSetCode = "447562003";
        final CompletableFuture<Void> warmup = SnowstormMapping.putInFlightWarmupForTest(branch, mapSetCode);
        try {
            warmup.complete(null);
            SnowstormMapping.awaitEclCacheWarmup(branch, mapSetCode, 200L);
        } finally {
            SnowstormMapping.clearInFlightWarmupForTest(branch, mapSetCode);
        }
    }

    @Test
    public void searchThrowsServiceUnavailableWhenWarmupExceedsWait() {

        final String branch = "MAIN/TEST-TIMEOUT";
        final String mapSetCode = "447562003";
        final CompletableFuture<Void> warmup = SnowstormMapping.putInFlightWarmupForTest(branch, mapSetCode);
        try {
            final RestException thrown = assertThrows(RestException.class, () -> SnowstormMapping.awaitEclCacheWarmup(branch, mapSetCode, 50L));
            assertEquals(503, thrown.getError().getStatus());
            assertEquals(SnowstormMapping.ECL_CACHE_UPDATING_MESSAGE, thrown.getError().getMessage());
        } finally {
            warmup.complete(null);
            SnowstormMapping.clearInFlightWarmupForTest(branch, mapSetCode);
        }
    }
}
