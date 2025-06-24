/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm.export;

import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;

/**
 * Interface for map set export strategies.
 */
public interface MapSetExportStrategy {

    /**
     * Export the map set according to the strategy.
     *
     * @param mapSet the map set
     * @param mapProject the map project
     * @param request the export request
     * @return the export file name
     * @throws Exception the exception
     */
    public String export(final MapSet mapSet, final MapProject mapProject, final MapSetExportRequest request) throws Exception;
}