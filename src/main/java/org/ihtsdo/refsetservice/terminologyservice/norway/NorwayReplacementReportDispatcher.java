/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice.norway;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts Norway reports asynchronously and guards against concurrent runs.
 */
public final class NorwayReplacementReportDispatcher {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(NorwayReplacementReportDispatcher.class);

    /** Guard for map report. */
    private static final AtomicBoolean MAP_RUNNING = new AtomicBoolean(false);

    /** Guard for translation report. */
    private static final AtomicBoolean TRANSLATION_RUNNING = new AtomicBoolean(false);

    /** Guard for Helsedirektoratet untranslated report. */
    private static final AtomicBoolean HELSEDIREKTORATET_UNTRANSLATED_RUNNING = new AtomicBoolean(false);

    /**
     * Instantiates a new norway replacement report dispatcher.
     */
    private NorwayReplacementReportDispatcher() {

        // n/a
    }

    /**
     * Starts the Norway replacement map report in the background.
     *
     * @throws RestException if already running or recipients are not configured
     */
    public static void startMapReport() {

        requireRecipients(NorwayReplacementReportSupport.MAP_RECIPIENTS_PROPERTY);
        if (!MAP_RUNNING.compareAndSet(false, true)) {
            throw new RestException(false, 409, "Conflict", "Norway replacement map report is already running");
        }
        CompletableFuture.runAsync(() -> {
            try {
                new NorwayReplacementMapReportService().runReport();
            } catch (final Exception e) {
                LOG.error("Norway replacement map report failed", e);
            } finally {
                MAP_RUNNING.set(false);
            }
        });
    }

    /**
     * Starts the Norway replacement translation report in the background.
     *
     * @throws RestException if already running or recipients are not configured
     */
    public static void startTranslationReport() {

        requireRecipients(NorwayReplacementReportSupport.TRANSLATION_RECIPIENTS_PROPERTY);
        if (!TRANSLATION_RUNNING.compareAndSet(false, true)) {
            throw new RestException(false, 409, "Conflict", "Norway replacement translation report is already running");
        }
        CompletableFuture.runAsync(() -> {
            try {
                new NorwayReplacementTranslationReportService().runReport();
            } catch (final Exception e) {
                LOG.error("Norway replacement translation report failed", e);
            } finally {
                TRANSLATION_RUNNING.set(false);
            }
        });
    }

    /**
     * Starts the Norway Helsedirektoratet untranslated report in the background.
     *
     * @throws RestException if already running or recipients are not configured
     */
    public static void startHelsedirektoratetUntranslatedReport() {

        requireRecipients(NorwayReplacementReportSupport.HELSEDIREKTORATET_UNTRANSLATED_RECIPIENTS_PROPERTY);
        if (!HELSEDIREKTORATET_UNTRANSLATED_RUNNING.compareAndSet(false, true)) {
            throw new RestException(false, 409, "Conflict", "Norway Helsedirektoratet untranslated report is already running");
        }
        CompletableFuture.runAsync(() -> {
            try {
                new NorwayHelsedirektoratetUntranslatedReportService().runReport();
            } catch (final Exception e) {
                LOG.error("Norway Helsedirektoratet untranslated report failed", e);
            } finally {
                HELSEDIREKTORATET_UNTRANSLATED_RUNNING.set(false);
            }
        });
    }

    /**
     * Ensures recipients are configured (via env-backed properties; addresses are not committed).
     *
     * @param propertyKey the property key
     */
    private static void requireRecipients(final String propertyKey) {

        final String recipients = PropertyUtility.getProperty(propertyKey);
        if (StringUtils.isBlank(recipients)) {
            throw new RestException(false, 417, "Expectation Failed",
                "Report recipients are not configured. Set environment variable for property " + propertyKey);
        }
    }
}
