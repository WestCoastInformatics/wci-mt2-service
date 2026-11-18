/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.ihtsdo.refsetservice.model.Mapping;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link MappingBulkSave} hand-off and reservation.
 */
public class MappingBulkSaveUnitTest {

    /**
     * A save that finishes inside the wait is returned on this request.
     *
     * @throws Exception the exception
     */
    @Test
    public void awaitResultReturnsMappingsWhenSaveFinishesInTime() throws Exception {

        final Mapping mapping = new Mapping();
        mapping.setCode("1");
        final List<Mapping> mappings = Collections.singletonList(mapping);

        final Object result = MappingBulkSave.awaitResult(CompletableFuture.completedFuture(mappings), "job-1", 1_000L);

        assertSame(mappings, result);
    }

    /**
     * A save that outlasts the wait keeps running and returns the job URL used by map-set export.
     *
     * @throws Exception the exception
     */
    @Test
    @SuppressWarnings("unchecked")
    public void awaitResultReturnsJobUrlWhenSaveOutlastsTheGatewayWindow() throws Exception {

        final CompletableFuture<List<Mapping>> future = new CompletableFuture<>();

        final Object result = MappingBulkSave.awaitResult(future, "job-2", 20L);

        final Map<String, String> body = (Map<String, String>) result;
        assertEquals("job/job-2", body.get("url"));
        assertFalse(future.isCancelled());
        assertFalse(future.isDone());
    }

    /**
     * A save that fails inside the wait surfaces the worker exception.
     */
    @Test
    public void awaitResultThrowsTheSaveFailure() {

        final CompletableFuture<List<Mapping>> future = CompletableFuture.failedFuture(new IllegalStateException("snowstorm down"));

        final IllegalStateException failure = assertThrows(IllegalStateException.class, () -> MappingBulkSave.awaitResult(future, "job-3", 1_000L));

        assertEquals("snowstorm down", failure.getMessage());
    }

    /**
     * A second reservation for the same map set is refused until the first is released.
     */
    @Test
    public void reserveBlocksASecondSaveUntilRelease() {

        final String mapSetId = "reserve-" + System.nanoTime();
        assertTrue(MappingBulkSave.reserve(mapSetId));
        try {
            assertFalse(MappingBulkSave.reserve(mapSetId));
        } finally {
            MappingBulkSave.release(mapSetId);
        }
        assertTrue(MappingBulkSave.reserve(mapSetId));
        MappingBulkSave.release(mapSetId);
    }
}
