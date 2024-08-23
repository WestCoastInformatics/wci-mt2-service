/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.List;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to get refset member concept information from a terminology
 * service.
 */
/**
 * @author jesseefron
 *
 */
public final class MappingService {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(MappingService.class);

  /** The terminology handler. */
  private static TerminologyServerHandler terminologyHandler;

  static {

    // Instantiate terminology handler
    try {
      String key = "terminology.handler";
      String handlerName = PropertyUtility.getProperty(key);
      if (handlerName.isEmpty()) {
        throw new Exception("terminology.handler expected and does not exist.");
      }

      terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key,
          handlerName, TerminologyServerHandler.class);

    } catch (Exception e) {
      LOG.error("Failed to initialize terminology.handler - serious error", e);
      terminologyHandler = null;
    }
  }

  /**
   * Instantiates an empty {@link MappingService}.
   */
  private MappingService() {

    // n/a
  }

  /**
   * Returns the mappings.
   *
   * @param branch the branch
   * @param mapSetCode the map set code
   * @param searchParameters the search parameters
   * @param filter the filter
   * @param show overridden entries the show overridden entries
   * @param conceptCodes the concept codes
   * @return the mappings
   * @throws Exception the exception
   */
  public static ResultList<Mapping> getMappings(final String branch, final String mapSetCode,
    final SearchParameters searchParameters, final String filter, final boolean showOverriddenEntries, final List<String> conceptCodes)
    throws Exception {

    return terminologyHandler.getMappings(branch, mapSetCode, searchParameters, filter, showOverriddenEntries, conceptCodes);
  }

  /**
   * Returns the mapping.
   *
   * @param branch the branch
   * @param mapSetCode the map set code
   * @param conceptCode the concept code
   * @param show overridden entries the show overridden entries
   * @return the mapping
   * @throws Exception the exception
   */
  public static Mapping getMapping(final String branch, final String mapSetCode, final String conceptCode, final boolean showOverriddenEntries)
    throws Exception {

    return terminologyHandler.getMapping(branch, mapSetCode, conceptCode, showOverriddenEntries);
  }

  /**
   * Creates the mapping.
   *
   * @param mapProject the map project
   * @param branch the branch
   * @param mapSetCode the map set code
   * @param mapping the mapping
   * @return the mapping
   * @throws Exception the exception
   */
  public static List<Mapping> createMappings(final MapProject mapProject, final String branch, final String mapSetCode,
    final List<Mapping> mappings) throws Exception {

    return terminologyHandler.createMappings(mapProject, branch, mapSetCode, mappings);
  }

  /**
   * Update mapping.
   *
   * @param mapProject the map project
   * @param branch the branch
   * @param mapSetCode the map set code
   * @param mapping the mapping
   * @throws Exception the exception
   */
  public static List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode,
    final List<Mapping> mapping) throws Exception {

    return terminologyHandler.updateMappings(mapProject, branch, mapSetCode, mapping);
  }
}
