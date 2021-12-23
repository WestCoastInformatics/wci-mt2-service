package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.SimpleDateFormat;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EditUnitTestUtilities {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(EditUnitTestUtilities.class);

    private MockMvc mvc;

    private String baseUrl;

    protected SimpleDateFormat sdf = null;

    public EditUnitTestUtilities(final MockMvc mvc, final String baseUrl,
            final SimpleDateFormat sdf) {
        this.mvc = mvc;
        this.baseUrl = baseUrl;
        this.sdf = sdf;
    }

    public String createNewRefsetVersion(String refsetId) {

        try {
            final String url = baseUrl + "/" + refsetId + "/newVersion";
            logger.info("Testing url - " + url);

            final ObjectNode newVersionBody = new ObjectMapper().createObjectNode();// .put("readVersion",
            // "");

            final MvcResult newVersionResult = mvc
                    .perform(post(url).content(newVersionBody.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            final String newVersionContent = newVersionResult.getResponse().getContentAsString();
            logger.info(" content = " + newVersionContent);

            final JsonNode newVersionRoot = new ObjectMapper().readTree(newVersionContent);
            final JsonNode newVersionNode = newVersionRoot;

            assertThat(newVersionNode.has("refsetInternalId")).isTrue();

            final String newRefsetInternalId = newVersionNode.get("refsetInternalId").asText();
            assertThat(newRefsetInternalId).isNotNull();
            return newRefsetInternalId;

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public void modifyRefsetMetadata(String refsetId, Map<String, String> modifyData) {

        try {
            final String modifyUrl = baseUrl + "/" + refsetId;
            logger.info("Testing url - " + modifyUrl);

            // the body of the modification call
            final ObjectNode modifyBody = new ObjectMapper().createObjectNode()
                    .put("narrative", modifyData.get("narrative"))
                    .put("versionNotes", modifyData.get("versionNotes"))
                    .set("tags", new ObjectMapper().createArrayNode().add(modifyData.get("tag1"))
                            .add(modifyData.get("tag2")));

            final MvcResult modifyResult = mvc.perform(put(modifyUrl).content(modifyBody.toString())
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            final String modifyContent = modifyResult.getResponse().getContentAsString();
            logger.info(" content = " + modifyContent);

            final JsonNode modifyRoot = new ObjectMapper().readTree(modifyContent);
            assertThat(modifyRoot.has("refsetInternalId")).isTrue();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteRefset(String refsetId) {

        try {
            final String deleteUrl = baseUrl + "/" + refsetId + "/editVersion";
            final MvcResult deleteResult =
                    mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
            final String deleteContent = deleteResult.getResponse().getContentAsString();
            final JsonNode deleteRoot = new ObjectMapper().readTree(deleteContent);
            final JsonNode deleteNode = deleteRoot;

            assertThat(deleteNode.has("status")).isTrue();
            assertThat(deleteNode.get("status").asText().equals("deleted")).isTrue();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
