/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to West Coast Informatics
 * and may be covered by U.S. and Foreign Patents, patents in process, and are protected by trade
 * secret or copyright law.  Dissemination of this information or reproduction of this material is
 * strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm.export;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.ihtsdo.refsetservice.handler.snowstorm.SnomedConstants;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormDescription;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for appending descriptions to RF2 files.
 */
public class DescriptionAppender {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(DescriptionAppender.class);

    /** The Constant CONCEPT_DESCRIPTIONS_PER_CALL. */
    // limited because of URL length
    private static final int CONCEPT_DESCRIPTIONS_PER_CALL = 230;

    /** The Constant DESCRIPTION_EXECUTOR_SIZE. */
    // Use a bounded thread pool with a reasonable size
    private static final int DESCRIPTION_EXECUTOR_SIZE = 10;

    /**
     * Instantiates a new description appender.
     *
     * @param snowstormDescription the snowstorm description
     */
    public DescriptionAppender() {

        // default constructor
    }

    /**
     * Append names to RF2 file.
     *
     * @param edition the edition
     * @param branchPath the branch path
     * @param origFilePath the original file path
     * @param newFileWithNamesPath the new file with names path
     * @param languageId the language id
     * @throws Exception the exception
     */
    public void appendNamesToRf2(final Edition edition, final String branchPath, final String origFilePath, final String newFileWithNamesPath,
        final String languageId) throws Exception {

        LOG.info("Appending descriptions to RF2 file");

        // First pass: collect all unique concept IDs
        final Set<String> uniqueConceptIds = new HashSet<>();
        try (final BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {
            // Skip header
            String line = br.readLine();
            while ((line = br.readLine()) != null && !line.trim().isEmpty()) {
                final String conceptId = line.split("\t")[SnomedConstants.REFEST_RF2_CONCEPTID_COLUMN];
                uniqueConceptIds.add(conceptId);
            }
        }

        // Split concept IDs into batches of 230
        final List<Set<String>> conceptBatches = new ArrayList<>();
        final Set<String> currentBatch = new HashSet<>();
        for (final String conceptId : uniqueConceptIds) {
            currentBatch.add(conceptId);
            if (currentBatch.size() >= CONCEPT_DESCRIPTIONS_PER_CALL) {
                conceptBatches.add(new HashSet<>(currentBatch));
                currentBatch.clear();
            }
        }
        if (!currentBatch.isEmpty()) {
            conceptBatches.add(currentBatch);
        }

        // Create a thread-safe map to store results
        final Map<String, Concept> members = Collections.synchronizedMap(new HashMap<>());
        LOG.info("Using executor size: {}", DESCRIPTION_EXECUTOR_SIZE);
        final ExecutorService executor = Executors.newFixedThreadPool(DESCRIPTION_EXECUTOR_SIZE);
        final List<Future<?>> futures = new ArrayList<>();

        final long startTime = System.currentTimeMillis();
        int batchCount = 0;

        // Submit tasks for each batch
        for (final Set<String> batch : conceptBatches) {
            if (batch.isEmpty()) {
                continue;
            }
            batchCount++;
            final int currentBatchNumber = batchCount;
            futures.add(executor.submit(() -> {
                try {
                    LOG.debug("Processing batch {} of {} with {} concepts", currentBatchNumber, conceptBatches.size(), batch.size());
                    final List<Concept> concepts = new ArrayList<>();
                    for (final String conceptId : batch) {
                        final Concept concept = new Concept();
                        concept.setCode(conceptId);
                        concepts.add(concept);
                    }

                    SnowstormDescription.populateAllLanguageDescriptions(edition, branchPath, concepts);

                    // Store results in thread-safe map
                    for (final Concept concept : concepts) {
                        members.put(concept.getCode(), concept);
                    }
                    LOG.debug("Completed batch {} of {}", currentBatchNumber, conceptBatches.size());
                } catch (Exception e) {
                    LOG.error("Error processing batch {} of {}: {}", currentBatchNumber, conceptBatches.size(), e.getMessage(), e);
                    throw new RuntimeException("Failed to process batch " + currentBatchNumber, e);
                }
            }));
        }

        // Wait for all tasks to complete
        for (final Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.MINUTES); // Add timeout to prevent hanging
            } catch (final Exception e) {
                LOG.error("Error waiting for task completion: {}", e.getMessage(), e);
                executor.shutdownNow(); // Force shutdown on error
                throw new RuntimeException("Failed to complete all batches", e);
            }
        }

        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            LOG.warn("Executor did not terminate within timeout");
            executor.shutdownNow();
        }

        final long endTime = System.currentTimeMillis();
        LOG.info("Processing completed in {} ms for {} batches", (endTime - startTime), batchCount);

        // Write the output file with the collected results
        try (final FileWriter fw = new FileWriter(new File(newFileWithNamesPath));

            final BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {

            // Write header
            final String headerLine = br.readLine();
            for (final Map<String, String> defaultLanguages : edition.getFullyQualifiedLanguageRefsets()) {
                if (languageId.equals(defaultLanguages.get("qualifiedLanguageRefset"))) {
                    fw.write(headerLine + "\t" + defaultLanguages.get("qualifiedLanguageCode") + "\n");
                }
            }

            // Write data rows
            String line;
            while ((line = br.readLine()) != null) {
                final String conceptId = line.split("\t")[SnomedConstants.REFEST_RF2_CONCEPTID_COLUMN];
                Concept concept = members.get(conceptId);

                if (concept == null) {
                    throw new Exception("Didn't have concept populated with descriptions yet");
                }

                boolean matchingDescriptionFound = false;
                String fallbackDescription = null;

                for (final Map<String, String> description : concept.getDescriptions()) {
                    if (description == null || !languageId.equals(description.get(SnomedConstants.LANGUAGE_ID))) {
                        if (description != null && description.get(SnomedConstants.LANGUAGE_ID).equals(SnomedConstants.DEFAULT_LANGUAGE_REFSET_US)) {
                            fallbackDescription = line + "\t" + description.get(SnomedConstants.DESCRIPTION_TERM);
                        }
                        continue;
                    }

                    fw.write(line + "\t" + description.get(SnomedConstants.DESCRIPTION_TERM));
                    matchingDescriptionFound = true;
                    break;
                }

                if (!matchingDescriptionFound && fallbackDescription != null) {
                    fw.write(fallbackDescription);
                } else if (!matchingDescriptionFound) {
                    fw.write(line);
                }

                fw.write("\n");
            }
        }
    }
}
