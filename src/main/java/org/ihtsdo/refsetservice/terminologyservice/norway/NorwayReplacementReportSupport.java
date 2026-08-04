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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.EmailUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared helpers for Norway replacement reports (file/zip/email/branch helpers).
 */
public final class NorwayReplacementReportSupport {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(NorwayReplacementReportSupport.class);

    /** Column delimiter used in TSV output. */
    public static final String COLUMN_DELIMITER = "\t";

    /** International map module. */
    public static final String INTERNATIONAL_MODULE = "449080006";

    /** Norwegian map module. */
    public static final String NORWAY_MODULE = "51000202101";

    /** Property key for map report recipients (env-backed; do not commit addresses). */
    public static final String MAP_RECIPIENTS_PROPERTY = "mail.smtp.norway.replacement.map.report.to";

    /** Property key for translation report recipients (env-backed; do not commit addresses). */
    public static final String TRANSLATION_RECIPIENTS_PROPERTY = "mail.smtp.norway.replacement.translation.report.to";

    /**
     * Instantiates a new norway replacement report support.
     */
    private NorwayReplacementReportSupport() {

        // n/a
    }

    /**
     * Returns true for Norway "garbage" SCTIDs (positions 7–11 are {@code 10002}).
     *
     * @param conceptId the concept id
     * @return true if garbage
     */
    public static boolean isGarbageSctid(final String conceptId) {

        return conceptId != null && conceptId.length() > 11 && "10002".equals(conceptId.substring(6, 11));
    }

    /**
     * Resolves current and previous version branch paths for SNOMEDCT-NO.
     *
     * @param client the Norway Snowstorm client
     * @return array of [currentBranch, previousVersionBranch]
     * @throws Exception the exception
     */
    public static String[] resolveBranchPaths(final NorwaySnowstormClient client) throws Exception {

        final JsonNode doc = client.getJson("/codesystems?forBranch=MAIN%2FSNOMEDCT-NO%2FREFSETS");
        final JsonNode items = doc.get("items");
        if (items == null || items.size() == 0) {
            throw new LocalException("No codesystem items returned for MAIN/SNOMEDCT-NO/REFSETS");
        }
        final JsonNode node = items.get(0);
        final String currentBranch = node.get("branchPath").asText();
        final String previousVersionBranch = node.get("latestVersion").get("branchPath").asText();
        return new String[] {
            currentBranch, previousVersionBranch
        };
    }

    /**
     * Writes TSV lines to a temp file named {@code filenamePrefix + ".txt"}.
     *
     * @param filenamePrefix filename prefix beginning with {@code /} (legacy style), without extension
     * @param lines the lines
     * @return the result file
     * @throws Exception the exception
     */
    public static File writeResultFile(final String filenamePrefix, final List<String> lines) throws Exception {

        final File resultFile = new File(System.getProperty("java.io.tmpdir") + filenamePrefix + ".txt");
        LOG.info("Created result file: {}", resultFile.getAbsolutePath());
        try (FileWriter writer = new FileWriter(resultFile)) {
            for (final String line : lines) {
                writer.write(line);
                writer.write(System.getProperty("line.separator"));
            }
        }
        return resultFile;
    }

    /**
     * Zips a result file next to it as {@code filenamePrefix + ".zip"}.
     *
     * @param filenamePrefix filename prefix beginning with {@code /}, without extension
     * @param resultFile the result file
     * @return the zip file
     * @throws Exception the exception
     */
    public static File zipResultFile(final String filenamePrefix, final File resultFile) throws Exception {

        final File zipFile = new File(System.getProperty("java.io.tmpdir") + filenamePrefix + ".zip");
        try (FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(fos));
            FileInputStream fis = new FileInputStream(resultFile)) {

            final ZipEntry ze = new ZipEntry(resultFile.getName());
            LOG.info("Zipping the file: {}", resultFile.getName());
            zipOut.putNextEntry(ze);
            final byte[] tmp = new byte[4 * 1024];
            int size;
            while ((size = fis.read(tmp)) != -1) {
                zipOut.write(tmp, 0, size);
            }
            zipOut.closeEntry();
        }
        return zipFile;
    }

    /**
     * Emails the report zip to configured recipients. No-ops (with warning) if recipients are blank.
     *
     * @param subject the subject
     * @param body the body
     * @param recipientsPropertyKey property key for recipients
     * @param zipFile the zip attachment
     * @throws Exception the exception
     */
    public static void emailReportFile(final String subject, final String body, final String recipientsPropertyKey, final File zipFile) throws Exception {

        final String recipients = PropertyUtility.getProperty(recipientsPropertyKey);
        if (StringUtils.isBlank(recipients)) {
            LOG.warn("No recipients configured for property {}; report zip left at {}", recipientsPropertyKey, zipFile.getAbsolutePath());
            return;
        }
        LOG.info("Request to send notification email for property {}", recipientsPropertyKey);
        EmailUtility.sendEmailWithAttachment(subject, recipients, body, zipFile.getAbsolutePath());
    }

    /**
     * Emails a report error (no attachment). No-ops if recipients are blank.
     *
     * @param subject the subject
     * @param body the body
     * @param recipientsPropertyKey property key for recipients
     * @throws Exception the exception
     */
    public static void emailReportError(final String subject, final String body, final String recipientsPropertyKey) throws Exception {

        final String recipients = PropertyUtility.getProperty(recipientsPropertyKey);
        if (StringUtils.isBlank(recipients)) {
            LOG.warn("No recipients configured for property {}; skipping error email", recipientsPropertyKey);
            return;
        }
        LOG.info("Request to send error notification email for property {}", recipientsPropertyKey);
        EmailUtility.sendEmail(subject, recipients, body);
    }

    /**
     * Returns a path separator suitable for logging/tmp paths.
     *
     * @return separator
     */
    public static String separator() {

        return FileSystems.getDefault().getSeparator();
    }
}
