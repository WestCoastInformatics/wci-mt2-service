/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class ExportUnitTestUtilities.
 */
public class ExportUnitTestUtilities {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ExportUnitTestUtilities.class);

    /** The mvc. */
    private MockMvc mvc;

    /**
     * Instantiates a {@link ExportUnitTestUtilities} from the specified parameters.
     *
     * @param mvc the mvc
     */
    public ExportUnitTestUtilities(final MockMvc mvc) {

        this.mvc = mvc;
    }

    /**
     * Export sct ids.
     *
     * @param internalRefsetId the internal refset id
     * @return the json node
     */
    public JsonNode exportSctIds(final String internalRefsetId) {

        try {
            final String url = "/export/" + internalRefsetId + "/?format=sctids";
            LOG.info("Export SctId url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(content);

            assertThat(root).isNotNull();
            return root;
        } catch (final Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    /**
     * Export rf 2 snapshot.
     *
     * @param internalRefsetId the internal refset id
     * @param exportVersion the export version
     * @return the json node
     */
    public JsonNode exportRf2Snapshot(final String internalRefsetId, final String exportVersion) {

        final String format = "SNAPSHOT";
        final String url = "/export/" + internalRefsetId + "/?format=rf2" + "&exportType=" + format + "&fileNameDate=" + exportVersion
            + "&transientEffectiveTime=" + exportVersion + "&languageId=900000000000509007FSN";

        return generateExport(url);
    }

    /**
     * Export rf 2 delta.
     *
     * @param internalRefsetId the internal refset id
     * @param exportFromVersion the export from version
     * @param exportToVersion the export to version
     * @return the json node
     */
    public JsonNode exportRf2Delta(final String internalRefsetId, final String exportFromVersion, final String exportToVersion) {

        final String format = "DELTA";
        final String url = "/export/" + internalRefsetId + "/?format=rf2" + "&exportType=" + format + "&fileNameDate=" + exportToVersion
            + "&startEffectiveTime=" + exportFromVersion + "&transientEffectiveTime=" + exportToVersion + "languageId=900000000000509007PT&";

        return generateExport(url);
    }

    /**
     * Generate export.
     *
     * @param url the url
     * @return the json node
     */
    private JsonNode generateExport(final String url) {

        try {
            LOG.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(content);

            return root;
        } catch (final Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    /**
     * Delete refset exports from aws.
     *
     * @param refsetId the refset id
     * @param exportVersion the export version
     */
    public void deleteRefsetExportsFromAws(final String refsetId, final String exportVersion) {

        try {
            final ExportHandler exporter = new ExportHandler();
            final String awsPath = exporter.getTopLevelAwsPath() + refsetId + "/" + exportVersion;
            S3ConnectionWrapper.deleteObjectFromAws(awsPath);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete refset exports from aws all versions.
     *
     * @param refsetId the refset id
     */
    public void deleteRefsetExportsFromAwsAllVersions(final String refsetId) {

        try {
            final ExportHandler exporter = new ExportHandler();
            final String awsPath = exporter.getTopLevelAwsPath() + refsetId;
            S3ConnectionWrapper.deleteObjectFromAws(awsPath);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
