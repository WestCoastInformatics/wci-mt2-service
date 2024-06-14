package org.ihtsdo.refsetservice.handler.snowstorm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SnowstormDescription extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormDescription.class);

  /**
   * Populate all language descriptions.
   *
   * @param refset the refset
   * @param conceptsToProcess the concepts to process
   * @throws Exception the exception
   */
  public static void populateAllLanguageDescriptions(final Refset refset,
    final List<Concept> conceptsToProcess) throws Exception {

    if (conceptsToProcess == null || conceptsToProcess.isEmpty()) {
      return;
    }

    // Create Snowstorm URL
    final String fullSnowstormUrl = SnowstormConnection.getBaseUrl()
        + RefsetMemberService.getBranchPath(refset) + "/descriptions?limit="
        + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + "&conceptIds="
        + conceptsToProcess.stream().map(Concept::getCode).collect(Collectors.joining(","));

    // Call Snowstorm
    try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

        throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);
      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode root = mapper.readTree(resultString.toString());

      final JsonNode allDescriptionNodes = root.get("items");
      final Iterator<JsonNode> descriptionIterator = allDescriptionNodes.iterator();
      final HashMap<String, Set<JsonNode>> conceptDescriptionNodes = new HashMap<>();

      // Assign descriptions to proper concept
      while (descriptionIterator.hasNext()) {

        final JsonNode descriptionNode = descriptionIterator.next();

        if (descriptionNode.get("active").asBoolean()) {

          final String conceptId = descriptionNode.get("conceptId").asText();

          if (!conceptDescriptionNodes.containsKey(conceptId)) {

            conceptDescriptionNodes.put(conceptId, new HashSet<JsonNode>());
          }

          conceptDescriptionNodes.get(conceptId).add(descriptionNode);
        }

      }

      // Process and sort each concept's descriptions
      final Map<String, List<Map<String, String>>> conceptDescriptionMap = new HashMap<>();

      final List<String> nonDefaultPreferredTerms =
          RefsetMemberService.identifyNonDefaultPreferredTerms(refset.getEdition());

      for (final String conceptId : conceptDescriptionNodes.keySet()) {

        final Set<JsonNode> descriptionNodes = conceptDescriptionNodes.get(conceptId);
        Set<Map<String, String>> descriptions = new HashSet<>();

        descriptions = RefsetMemberService.processDescriptionNodes(descriptionNodes,
            refset.getEdition().getDefaultLanguageRefsets(), nonDefaultPreferredTerms);

        final List<Map<String, String>> sortedDescriptions = RefsetMemberService
            .sortConceptDescriptions(conceptId, descriptions, refset, nonDefaultPreferredTerms);
        conceptDescriptionMap.put(conceptId, sortedDescriptions);
      }

      // Populate concept with description-based data
      for (final Concept concept : conceptsToProcess) {

        final List<Map<String, String>> descriptions = conceptDescriptionMap.get(concept.getCode());

        if (descriptions == null || descriptions.size() == 0) {

          LOG.debug("Description not retrieved for concept " + concept.getCode());
          continue;
        }

        concept.setDescriptions(descriptions);

        if (descriptions.get(0) != null) {

          concept.setName(descriptions.get(0).get(RefsetMemberService.DESCRIPTION_TERM));
        } else {

          for (final Map<String, String> description : descriptions) {

            if (description == null) {

              continue;
            }

            if (description.get(RefsetMemberService.LANGUAGE_ID)
                .equals(RefsetMemberService.PREFERRED_TERM_EN)) {

              concept.setName(description.get(RefsetMemberService.DESCRIPTION_TERM));
              break;
            }

          }

        }

      }

    } catch (final Exception ex) {

      LOG.error("Could not retrieve descriptions " + ex.getMessage());
      ex.printStackTrace();
    }
  }

}
