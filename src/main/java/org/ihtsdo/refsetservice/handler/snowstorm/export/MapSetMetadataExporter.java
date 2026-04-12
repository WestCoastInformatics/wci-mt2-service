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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for exporting map set metadata.
 */
public class MapSetMetadataExporter {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MapSetMetadataExporter.class);

    /**
     * Instantiates a new map set metadata exporter.
     */
    public MapSetMetadataExporter() {

        // Constructor
    }

    /**
     * Export map set metadata.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSet the mapset
     * @param type the type
     * @param directory the directory
     * @param transientEffectiveTime the transient effective time
     * @return the string
     * @throws Exception the exception
     */
    public String exportMapSetMetadata(final MapProject mapProject, final String branch, final MapSet mapSet, final FileFormatType type, final String directory,
        final String transientEffectiveTime) throws Exception {

        final StringBuilder fileLines = new StringBuilder();

        final String outputPath = "der2_iisssccRefset" + mapProject.getDestinationTerminology() + "ExtendedMap" + mapSet.getRefSetCode()
            + StringUtility.capitalizeEachWord(mapProject.getEdition().getAbbreviation()) + "_" + transientEffectiveTime + "_metadata.txt";

        final String separator = "\t";
        final String LINE_FEED = "\n";

        fileLines.append("Mapset ID" + separator + mapSet.getRefSetCode() + LINE_FEED);
        fileLines.append("Mapset Name" + separator + mapSet.getRefSetName() + LINE_FEED);
        fileLines.append("Edition Name" + separator + mapProject.getEdition().getShortName() + LINE_FEED);
        fileLines.append("Edition Branch" + separator + branch + LINE_FEED);
        fileLines.append("Organization" + separator + mapProject.getEdition().getOrganization().getName() + LINE_FEED);
        fileLines.append("Project" + separator + mapProject.getName() + LINE_FEED);
        fileLines.append("Module ID" + separator + mapSet.getModuleId() + LINE_FEED);
        fileLines.append("Mapset Version Status" + separator + mapSet.getVersionStatus() + LINE_FEED); // PUBLISHED

        if (mapSet.getModified() != null) { // Version Date
            fileLines
                .append("MapSet Version Date" + separator + DateUtility.formatDate(mapSet.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + LINE_FEED);
        } else {
            fileLines.append("MapSet Version Date" + separator + LINE_FEED);
        }

        fileLines
            .append("MapSet Last Modified Date" + separator + DateUtility.formatDate(mapSet.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + LINE_FEED);
        fileLines.append("MapSet Type" + separator + type + LINE_FEED);

        if (mapSet.isActive()) {
            fileLines.append("MapSet Status" + separator + "Active" + LINE_FEED);
        } else {
            fileLines.append("MapSet Status" + separator + "Inactive" + LINE_FEED);
        }

        if (mapSet.getRefSetName() != null && !mapSet.getRefSetName().isEmpty()) { // getNarrative
            fileLines.append("MapSet Narrative" + separator + mapSet.getRefSetName() + LINE_FEED);
        }

        try {
            final Path path = Paths.get(directory, outputPath);
            Files.write(path, fileLines.toString().getBytes(StandardCharsets.UTF_8));
            return path.toString();
        } catch (IOException ex) {
            throw new Exception("Could not create metadata mapset export txt file: " + ex.getMessage(), ex);
        }
    }
}