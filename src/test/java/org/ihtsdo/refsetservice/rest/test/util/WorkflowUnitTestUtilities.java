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

public class WorkflowUnitTestUtilities {
    private static Logger logger = LoggerFactory.getLogger(WorkflowUnitTestUtilities.class);

    private MockMvc mvc;

    public WorkflowUnitTestUtilities(final MockMvc mvc) {
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

}
