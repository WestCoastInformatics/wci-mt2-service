package org.ihtsdo.refsetservice.rest.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Iterator;
import java.util.Map.Entry;

import org.ihtsdo.refsetservice.test.BaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates the generated OpenAPI 3 document.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OpenApiSpecUnitTest extends BaseTest {

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /**
     * OpenAPI document is parseable, has bearerAuth, and success JSON responses have content.
     *
     * @throws Exception the exception
     */
    @Test
    public void openApiDocumentIsValid() throws Exception {

        final String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        final JsonNode root = new ObjectMapper().readTree(json);

        assertTrue(root.path("openapi").asText().startsWith("3."), "openapi version");
        assertTrue(root.path("paths").size() > 0, "paths present");
        assertFalse(root.at("/components/securitySchemes/bearerAuth").isMissingNode(), "bearerAuth scheme");

        boolean sawMapAdvicePostBody = false;
        boolean sawConceptGetContent = false;
        final Iterator<Entry<String, JsonNode>> paths = root.path("paths").properties().iterator();
        while (paths.hasNext()) {
            final Entry<String, JsonNode> path = paths.next();
            final Iterator<Entry<String, JsonNode>> methods = path.getValue().properties().iterator();
            while (methods.hasNext()) {
                final Entry<String, JsonNode> method = methods.next();
                if (!method.getValue().isObject()) {
                    continue;
                }
                final JsonNode operation = method.getValue();
                if ("/mapAdvice".equals(path.getKey()) && "post".equals(method.getKey())) {
                    assertFalse(operation.path("requestBody").path("content").path("application/json").path("schema").isMissingNode(),
                        "POST /mapAdvice requestBody schema");
                    sawMapAdvicePostBody = true;
                }
                if (path.getKey().contains("/concept/{terminology}/{version}/{code}") && "get".equals(method.getKey())) {
                    assertFalse(operation.at("/responses/200/content").isMissingNode(), "GET concept 200 content");
                    sawConceptGetContent = true;
                }
            }
        }
        assertTrue(sawMapAdvicePostBody, "POST /mapAdvice documented");
        assertTrue(sawConceptGetContent, "GET concept documented");
    }
}
