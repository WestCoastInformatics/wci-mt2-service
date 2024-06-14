package org.ihtsdo.refsetservice.handler.snowstorm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SnowstormMultiSearch extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormMultiSearch.class);

  /**
   * Gets the directory members.
   *
   * @param snowstormQuery the snowstorm query
   * @return the directory members
   * @throws Exception the exception
   */
  public static Set<String> getDirectoryMembers(final String snowstormQuery) throws Exception {

    // if there are no query terms just exit the method
    if (StringUtils.isBlank(snowstormQuery)) {
      return new HashSet<>();
    }

    // refsetId: "12345" AND privateRefset:false AND term:"blood" AND
    // name:"work"
    final Set<String> refsetIds = new HashSet<>();

    final String url = SnowstormConnection.getBaseUrl()
        + "multisearch/descriptions/referencesets?active=true&offset=0&limit=1&term="
        + StringUtility.encodeValue(snowstormQuery);

    LOG.debug("Snowstorm URL: " + url);

    try (final Response response = SnowstormConnection.getResponse(url)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

        throw new Exception("call to url '" + url + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);

      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode root = mapper.readTree(resultString.toString());

      if (root.get("buckets") != null) {

        final Iterator<String> membershipIterator =
            root.get("buckets").get("membership").fieldNames();

        while (membershipIterator.hasNext()) {

          refsetIds.add(membershipIterator.next());
        }

      }

    } catch (final Exception ex) {

      throw new Exception(
          "Could not retrieve Reference Set members from snowstorm: " + ex.getMessage(), ex);
    }

    LOG.debug("searchDirectoryMembers refsetQuery: {}", refsetIds);
    return refsetIds;
  }

  /**
   * Search multisearch descriptions.
   *
   * @param searchParameters the search parameters
   * @param ecl the ecl
   * @param nonPublishedBranchPaths the non published branch paths
   * @return the sets the
   * @throws Exception the exception
   */
  public static Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters,
    final String ecl, final Set<String> nonPublishedBranchPaths) throws Exception {

    final String query = searchParameters.getQuery();
    final List<String> directoryColumns = Arrays.asList("id", "refsetId", "name", "editionName",
        "organizationName", "versionStatus", "versionDate", "modified", "privateRefset");
    String snowstormQuery = "";
    final String[] queryParts = query.split(" AND ");
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode body = mapper.createObjectNode();
    final ArrayNode bodyPaths = mapper.createArrayNode();

    for (final String queryPart : queryParts) {

      final String[] keyValue = queryPart.split(":");

      if (keyValue.length > 1 && directoryColumns.contains(keyValue[0])) {
        continue;
      } else {
        snowstormQuery += queryPart + " AND ";
      }
    }

    snowstormQuery = StringUtils.removeEnd(snowstormQuery, " AND ");

    for (final String branchPath : nonPublishedBranchPaths) {
      bodyPaths.add(branchPath);
    }

    body.set("branches", bodyPaths);

    final String url = SnowstormConnection.getBaseUrl()
        + "multisearch/descriptions?active=true&offset=0&limit=10000" + "&ecl="
        + StringUtility.encodeValue(ecl) + "&term=" + StringUtility.encodeValue(snowstormQuery);

    LOG.debug("searchMultisearchDescriptions: Search Refset Concepts descriptions URL: " + url);
    LOG.debug(
        "!!!!! searchMultisearchDescriptions: Search Refset Concepts descriptions BODY: " + body);

    try (Response response = SnowstormConnection.postResponse(url, body.toString())) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
        throw new Exception("call to url '" + url + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);
      final JsonNode root = mapper.readTree(resultString.toString());

      final Set<String> conceptIds = new HashSet<>();

      if (root.get("items") != null) {

        final JsonNode itemsNode = root.get("items");

        if (itemsNode.isArray()) {

          for (final JsonNode itemNode : itemsNode) {
            conceptIds.add(itemNode.get("concept").get("conceptId").asText());
          }

        }

      }

      return conceptIds;
    }
  }

}
