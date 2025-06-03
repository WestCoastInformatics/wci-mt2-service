package org.ihtsdo.refsetservice.handler.snowstorm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.sync.util.SyncStatistics;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SnowstormCodeSystem extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormCodeSystem.class);

  /**
   * Gets the affiliate edition list.
   *
   * @return the affiliate edition list
   * @throws Exception the exception
   */
  public static List<Edition> getAffiliateEditionList() throws Exception {

    final List<Edition> editionList = new ArrayList<>();
    final String url = SnowstormConnection.getBaseUrl() + "codesystems";
    LOG.info("getSnowstormCodeSystems url: " + url);

    try (final Response response = SnowstormConnection.getResponse(url)) {

      final String resultString = response.readEntity(String.class);
      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());
      final SyncUtilities syncUtilities =
          new SyncUtilities(new SyncDatabaseHandler(null, new SyncStatistics()));

      final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();

      while (organizationIterator.hasNext()) {

        final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

        while (codeSystems.hasNext()) {

          final JsonNode codeSystem = codeSystems.next();

          // Check for invalid or ignored code systems
          if (!codeSystem.has("shortName")) {
            continue;
          }

          final String editionShortName = codeSystem.get("shortName").asText();
          final String maintainerType =
              syncUtilities.identifyMaintainerType(codeSystem, editionShortName);

          // Skip inactive code systems
          if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
            continue;
          }

          // Code System has been defined as to-be-ignored (either by specifying
          // name or
          // shortname).
          else if (syncUtilities.getPropertyReader().getCodeSystemsToIgnore()
              .contains(editionShortName)) {
            continue;
          }

          // deal only with Type-3 (non-Managed Service)
          // else if (!maintainerType.equalsIgnoreCase("Managed Service")) {
          // continue;
          // }

          // deal only with official affiliate code systems
          else if (!editionShortName.toLowerCase().contains("-affiliate")) {
            continue;
          }

          final String editionName = codeSystem.get("name").asText();
          final String branch = codeSystem.get("branchPath").asText();
          final String defaultLanguageCode =
              syncUtilities.identifyDefaultLanguageCode(codeSystem, editionName);
          final Set<String> defaultLanguageRefsets =
              syncUtilities.identifyDefaultLanguageRefsets(codeSystem, editionShortName, branch);

          // Affiliates (on any extension) should not have the ability to choose
          // modules.
          // Fix to 1201891009 |SNOMED CT Community content module (core
          // metadata
          // concept)| for all affiliate refsets
          final Set<String> editionModules = new HashSet<>(Arrays.asList("1201891009"));

          final Edition edition = new Edition();
          edition.setShortName(editionShortName);
          edition.setName(editionName);
          edition.setBranch(branch);
          edition.setDefaultLanguageRefsets(defaultLanguageRefsets);
          edition.setModules(editionModules);
          edition.setDefaultLanguageCode(defaultLanguageCode);
          edition.setMaintainerType(maintainerType);
          edition.setActive(true);

          editionList.add(edition);
        }
      }
    }

    return editionList;
  }
}
