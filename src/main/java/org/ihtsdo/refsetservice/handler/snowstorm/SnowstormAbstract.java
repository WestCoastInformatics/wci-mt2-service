package org.ihtsdo.refsetservice.handler.snowstorm;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormAbstract.class);

  /**
   * Format error message.
   *
   * @param response the response
   * @return the string
   */
  public static String formatErrorMessage(final Response response) {

    String snowstormErrorMessage = response.readEntity(String.class);
    if (StringUtils.isEmpty(snowstormErrorMessage)) {
      return "";
    }
    if (StringUtility.isJson(snowstormErrorMessage)) {
      final ObjectMapper mapper = new ObjectMapper();
      try {
        final JsonNode json = mapper.readTree(snowstormErrorMessage);
        snowstormErrorMessage = json.has("message") ? json.get("message").asText() : "";
      } catch (final Exception e) {
        LOG.error("formatErrorMessage snowstormErrorMessage:{}", snowstormErrorMessage, e);
      }
    }
    return snowstormErrorMessage.replaceAll("[\\r\\n]+", " ");
  }

}
