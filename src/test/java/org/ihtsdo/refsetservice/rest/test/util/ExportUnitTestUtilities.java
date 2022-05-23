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

public class ExportUnitTestUtilities {
    private static Logger logger = LoggerFactory.getLogger(ExportUnitTestUtilities.class);

    private MockMvc mvc;

    public ExportUnitTestUtilities(final MockMvc mvc) {
        this.mvc = mvc;
    }

    public JsonNode exportSctIds(String internalRefsetId) {

        try {
            final String url = "/export/" + internalRefsetId + "/?format=sctids";
            logger.info("Export SctId url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(content);

            assertThat(root).isNotNull();
            return root;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public JsonNode exportRf2Snapshot(String internalRefsetId, String exportVersion) {
        final String format = "SNAPSHOT";
        final String url = "/export/" + internalRefsetId + "/?format=rf2" + "&exportType=" + format
                + "&fileNameDate=" + exportVersion + "&transientEffectiveTime=" + exportVersion
                + "&languageId=900000000000509007FSN";

        return generateExport(url);
    }

    public JsonNode exportRf2Delta(String internalRefsetId, String exportFromVersion,
        String exportToVersion) {
        final String format = "DELTA";
        final String url = "/export/" + internalRefsetId + "/?format=rf2" + "&exportType=" + format
                + "&fileNameDate=" + exportToVersion + "&startEffectiveTime=" + exportFromVersion
                + "&transientEffectiveTime=" + exportToVersion + "languageId=900000000000509007PT&";

        return generateExport(url);
    }

    private JsonNode generateExport(String url) {
        try {
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(content);

            return root;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public void deleteRefsetExportsFromAws(String refsetId, String exportVersion) {
        try {
            S3ConnectionWrapper.connectToAmazonS3();
            ExportHandler exporter = new ExportHandler();
            String awsPath = exporter.getTopLevelAwsPath() + refsetId + "/" + exportVersion;
            S3ConnectionWrapper.deleteObjectFromAws(awsPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteRefsetExportsFromAwsAllVersions(String refsetId) {
        try {
            S3ConnectionWrapper.connectToAmazonS3();
            ExportHandler exporter = new ExportHandler();
            String awsPath = exporter.getTopLevelAwsPath() + refsetId;
            S3ConnectionWrapper.deleteObjectFromAws(awsPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
