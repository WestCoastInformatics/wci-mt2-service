/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.sync.SyncAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * The Class SyncPropertyFileReader.
 */
/**
 * @author jesseefron
 *
 */
public class SyncPropertyFileReader {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncPropertyFileReader.class);

    /** The projects resource. */
    private final ClassPathResource projectsResource = new ClassPathResource("sync/rtt-migration/projects.txt");

    /** The clauses resource. */
    private final ClassPathResource clausesResource = new ClassPathResource("sync/rtt-migration/clauses.txt");

    /** The refsets resource. */
    private final ClassPathResource refsetsResource = new ClassPathResource("sync/rtt-migration/refsets.txt");

    /** The refset rtt to sct id resource. */
    private final ClassPathResource refsetRttToSctIdResource = new ClassPathResource("sync/rtt-migration/refsetRttToSct.txt");

    /** The refset to tags resource. */
    private final ClassPathResource refsetToTagsResource = new ClassPathResource("sync/rtt-migration/refsetToTags.txt");

    /** The refset to projects resource. */
    private final ClassPathResource refsetToProjectsResource = new ClassPathResource("sync/rtt-migration/refsetToProjects.txt");

    /** The Constant IGNORED_CODE_SYSTEMS_PATH. */
    private static final String IGNORED_CODE_SYSTEMS_PATH = "sync/exceptions/ignoredCodeSystems.txt";

    /** The ignored code systems resource. */
    private ClassPathResource ignoredCodeSystemsResource = new ClassPathResource(IGNORED_CODE_SYSTEMS_PATH);

    /** The undefined default lang refsets resource. */
    private final ClassPathResource undefinedDefaultLangRefsetsResource = new ClassPathResource("sync/exceptions/undefinedDefaultLangRefsets.txt");

    /** The existing migrate clean resource. */
    private final ClassPathResource existingMigrateCleanResource = new ClassPathResource("sync/supporting-files/existingProjectNameIds.txt");

    /** The Constant SPLIT_CHARACTER. */
    public static final String SPLIT_CHARACTER = "\t";

    private static final String OLD_SNOMED_CORE_NAME = "IHTSDO";

    /** The refset sct id to tags map. */
    private final Map<String, Set<String>> refsetSctIdToTagsMap = readRefsetSctIdToTagsMap();

    /** The code system short names. */
    private final List<String> codeSystemShortNames = new ArrayList<>();

    /** The refset internal id map. */
    private final Map<String, String> rttIdToRefsetJsonMap = new HashMap<>();

    /** The refset sct id to internal id map. */
    private final Map<String, Set<String>> rttRefsetSctIdToRttIdMap = new HashMap<>();

    /** The rtt refset to clauses map. */
    private final Map<String, ArrayList<String>> refsetSctIdToClausesMap = new HashMap<>();

    /** The project data map of project line to name to description. */
    private final Set<SyncProjectMetadata> projectData = new HashSet<>();

    /** The projects to ignore. */
    private final Set<String> projectsToIgnore = new HashSet<>();

    /** The refset to project map. */
    private final Map<String, String> rttIdToRttProjectIdMap = new HashMap<>();

    /** The existing edition project info. */
    private final Map<String, Map<String, String>> existingEditionProjectInfo = readExistingEditionProjectInfo();

    /** The sct id to project id map. */
    private final Map<String, String> sctIdToProjectIdMap = new HashMap<>();

    /** Map of Project ids to map of project name-to-description. */
    private final Map<String, Map<String, String>> projectIdToProjectInfoMap = new HashMap<>();

    /** The default language refset map. */
    private static Map<String, Set<String>> defaultLanguageRefsetMap = new HashMap<>();

    /**
     * The Enum FileProcessType.
     */
    private enum FileProcessType {

        /** The refset. */
        REFSET,
        /** The clause. */
        CLAUSE,
        /** The project. */
        PROJECT;
    }

    /**
     * Pre-processing supporting files.
     *
     * @throws Exception the exception
     */
    public void parseRttData() throws Exception {

        // Based on findings, define the list of refsets in RTT
        populateFromFile(clausesResource, FileProcessType.CLAUSE);
        populateFromFile(projectsResource, FileProcessType.PROJECT);

        final BufferedReader reader = new BufferedReader(new InputStreamReader(refsetRttToSctIdResource.getInputStream()));

        // Grab Header on 2nd time through
        String line = reader.readLine();
        line = reader.readLine();

        while (line != null) {

            if (!rttRefsetSctIdToRttIdMap.containsKey(line.split(SPLIT_CHARACTER)[1])) {

                rttRefsetSctIdToRttIdMap.put(line.split(SPLIT_CHARACTER)[1], new HashSet<String>());
            }

            rttRefsetSctIdToRttIdMap.get(line.split(SPLIT_CHARACTER)[1]).add(line.split(SPLIT_CHARACTER)[0]);

            line = reader.readLine();
        }

        populateFromFile(refsetsResource, FileProcessType.REFSET);
    }

    /**
     * Returns the code systems to ignore.
     *
     * @return the code systems to ignore
     */
    // Reread every time as can now update list without rebuilding. Not an issue as it's only used via sync (so not costly)
    public List<String> getCodeSystemsToIgnore() {

        try {

            ignoredCodeSystemsResource = new ClassPathResource(IGNORED_CODE_SYSTEMS_PATH);

            final BufferedReader reader = new BufferedReader(new InputStreamReader(ignoredCodeSystemsResource.getInputStream()));

            String line = reader.readLine();

            while (line != null) {

                if (!line.isBlank()) {

                    final String shortName = line.split("\t")[0];

                    codeSystemShortNames.add(shortName);
                }

                line = reader.readLine();
            }

            reader.close();
        } catch (final IOException e) {

            e.printStackTrace();
        }

        return codeSystemShortNames;
    }

    /**
     * Read rtt project info.
     */
    private void readRttProjectInfo() {

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(refsetToProjectsResource.getInputStream()));

            // ProjectId, refsetId, projectName, projectDescription
            String line = reader.readLine();

            while (line != null && !line.isEmpty()) {

                final String[] columns = line.split(SPLIT_CHARACTER);

                sctIdToProjectIdMap.put(columns[1], columns[0]);

                if (!projectIdToProjectInfoMap.containsKey(columns[0])) {

                    final Map<String, String> projectNameDescriptionMap = new HashMap<>();
                    projectNameDescriptionMap.put(columns[2], columns[3]);
                    projectIdToProjectInfoMap.put(columns[0], projectNameDescriptionMap);
                }

                line = reader.readLine();
            }

            reader.close();
        } catch (final IOException e) {

            e.printStackTrace();
        }

    }

    /**
     * Read refset sct id to tags map.
     *
     * @return the map
     */
    private Map<String, Set<String>> readRefsetSctIdToTagsMap() {

        final Map<String, Set<String>> refsetToTagsInfoMap = new HashMap<>();

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(refsetToTagsResource.getInputStream()));

            String line = reader.readLine();

            while (line != null && !line.isEmpty()) {

                final String[] columns = line.split(SPLIT_CHARACTER);

                if (!refsetToTagsInfoMap.containsKey(columns[0])) {

                    refsetToTagsInfoMap.put(columns[0], new HashSet<>());
                }

                if (columns.length == 2 && !columns[0].isEmpty() && !columns[1].isEmpty() && refsetToTagsInfoMap.containsKey(columns[0])) {

                    refsetToTagsInfoMap.get(columns[0]).add(stripQuotes(columns[1]));
                } else {

                    LOG.info("Skipping this line in readRttRefsetsToTagsMap(): " + line);
                }

                line = reader.readLine();
            }

            reader.close();
        } catch (final IOException e) {

            e.printStackTrace();
        }

        return refsetToTagsInfoMap;
    }

    /**
     * Read undefined default language refsets.
     *
     * @return the map
     */
    public Map<String, Set<String>> readUndefinedDefaultLanguageRefsets() {

        if (defaultLanguageRefsetMap == null) {

            defaultLanguageRefsetMap = new HashMap<>();
        }

        if (defaultLanguageRefsetMap.isEmpty()) {

            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(undefinedDefaultLangRefsetsResource.getInputStream()));) {

                String line;

                while ((line = reader.readLine()) != null) {

                    final String[] columns = line.split("\t");
                    defaultLanguageRefsetMap.put(columns[0], new HashSet<String>());

                    for (int i = 1; i < columns.length; i++) {

                        defaultLanguageRefsetMap.get(columns[0]).add(columns[i]);
                    }

                }

            } catch (final IOException e) {

                e.printStackTrace();
            }

        }

        return defaultLanguageRefsetMap;
    }

    /**
     * Read rtt project info.
     *
     * @return the map
     */
    private Map<String, Map<String, String>> readExistingEditionProjectInfo() {

        // edition to map of project name to crowd id
        final Map<String, Map<String, String>> projectInfo = new HashMap<>();

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(existingMigrateCleanResource.getInputStream()));

            // Line contents: crowdProjectId, projectName, editionShortName
            String line = reader.readLine();
            line = reader.readLine();

            while (line != null && !line.isEmpty()) {

                final String[] columns = line.split(SPLIT_CHARACTER);

                if (!projectInfo.containsKey(columns[2])) {

                    projectInfo.put(columns[2], new HashMap<>());
                }

                projectInfo.get(columns[2]).put(columns[1], columns[0]);

                line = reader.readLine();
            }

            reader.close();
        } catch (final IOException e) {

            e.printStackTrace();
        }

        return projectInfo;
    }

    /**
     * Generate json from sql file.
     *
     * @param classPathResource the input resource
     * @param processType the process type
     * @throws Exception the exception
     */
    private void populateFromFile(final ClassPathResource classPathResource, final FileProcessType processType) throws Exception {

        int lineNumber = 0;

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(classPathResource.getInputStream()));

            // Grab Header on 2nd time through
            String line = reader.readLine();
            line = reader.readLine();

            while (line != null) {

                switch (processType) {

                    case REFSET:
                        final String refsetJson = lineToRefsetJson(line, lineNumber++);

                        if (refsetJson != null) {

                            rttIdToRefsetJsonMap.put(line.split(SPLIT_CHARACTER)[0], refsetJson);
                        }
                        break;

                    case CLAUSE:

                        // Combine multiline clauses into one
                        while (line.indexOf("\"") >= 0 && line.indexOf("\"") == line.lastIndexOf("\"")) {

                            line = line + " " + reader.readLine();
                        }

                        final String clauseJson = lineToClauseJson(line, lineNumber++);
                        final String refsetSctId = line.split(SPLIT_CHARACTER)[0];

                        // store all clauses associated wtih a given refset

                        if (!refsetSctIdToClausesMap.containsKey(refsetSctId)) {

                            refsetSctIdToClausesMap.put(refsetSctId, new ArrayList<String>());
                        }

                        refsetSctIdToClausesMap.get(refsetSctId).add(clauseJson);
                        break;

                    case PROJECT:
                        parseProjectLine(line, lineNumber++);
                        break;

                    default:
                        throw new Exception("Should never reach here have processType: " + processType);
                }

                // read next line
                line = reader.readLine();
            }

            reader.close();
        } catch (final IOException e) {

            e.printStackTrace();
        }

    }

    /**
     * Strip quotes.
     *
     * @param str the str
     * @return the string
     */
    private String stripQuotes(final String str) {

        String updatedString = str;

        if (updatedString.startsWith("\"")) {

            updatedString = updatedString.substring(1);
        }

        if (updatedString.endsWith("\"")) {

            updatedString = updatedString.substring(0, updatedString.length() - 1);
        }

        return updatedString;
    }

    /**
     * Line to clause json.
     *
     * @param line the line
     * @param lineNumber the line number
     * @return the string
     */
    private String lineToClauseJson(final String line, final int lineNumber) {

        try {

            final StringBuffer buf = new StringBuffer();
            final String[] clauseValues = line.split(SPLIT_CHARACTER);
            buf.append("{ \"negated\":\"");
            buf.append(clauseValues[1].equals("0") ? "false" : "true");
            buf.append("\",");

            buf.append("\"value\":\"" + clauseValues[2].replaceAll("\"", "").replaceAll("\t", "") + "\"}");
            return buf.toString();
        } catch (final Exception e) {

            LOG.error("Failed to process line #" + lineNumber + " of clause json: " + line);

            e.printStackTrace();

            throw e;
        }

    }

    /**
     * Line to refset json.
     *
     * @param line the line
     * @param lineNumber the line number
     * @return the string
     * @throws Exception the exception
     */
    private String lineToRefsetJson(final String line, final int lineNumber) throws Exception {

        String updatedLine = line;
        String narrative;

        try {

            // Clean up narrative if has commas which some do
            if (updatedLine.split(SPLIT_CHARACTER)[9].startsWith("\"")) {

                // Can't rely on splitting on comma. Must identify narrative and
                // then remove from line before finding other values
                final int descStartIdx = updatedLine.indexOf(updatedLine.split(SPLIT_CHARACTER)[9]);
                final int descEndIdx = updatedLine.substring(descStartIdx + 1).indexOf("\"");
                narrative = updatedLine.substring(descStartIdx + 1, descStartIdx + descEndIdx + 1);

                // Cleanup updateLine to remove ',' in narrative
                updatedLine =
                    updatedLine.substring(0, descStartIdx) + narrative.replaceAll(SPLIT_CHARACTER, "") + updatedLine.substring(descStartIdx + descEndIdx + 2);
            } else {

                narrative = updatedLine.split(SPLIT_CHARACTER)[9];
            }

            // Clean up name if has commas (which some do)
            if (updatedLine.split(SPLIT_CHARACTER)[17].startsWith("\"")) {

                // Can't rely on splitting on comma. Must identify name portion
                // and then remove from line before finding other values
                final int nameStartIdx = updatedLine.indexOf(updatedLine.split(SPLIT_CHARACTER)[17]);
                final int nameEndIdx = updatedLine.substring(nameStartIdx + 1).indexOf("\"");
                final String name = updatedLine.substring(nameStartIdx + 1, nameStartIdx + nameEndIdx + 1);

                // Cleanup line to remove ',' in narrative
                updatedLine = updatedLine.substring(0, nameStartIdx) + name.replaceAll(",", "") + updatedLine.substring(nameStartIdx + nameEndIdx + 2);
            }

            updatedLine = updatedLine.replace("\"", "");
            final String[] values = updatedLine.split(SPLIT_CHARACTER);

            if (projectsToIgnore.contains(values[27])) {

                // Don't add refsets from ignored projects (just WCI projects for now)
                return null;
            } else if (!values[8].matches("\\b\\d*\\b")) {

                return null;
            } else if (!"PUBLISHED".equals(values[26])) {

                // Only add published versions of refsets, not those in development
                return null;
            } else if (values[2] == null) {

                // Published refsets must have an effectiveTime
                return null;
            } else if (values[1].length() != 1 || (!values[1].equals("1") && !Character.isISOControl(values[1].charAt(0)))) {

                // Must be an active refset
                return null;
            }

            final StringBuffer buf = new StringBuffer();
            final String rttRefsetId = values[0];

            if (narrative.equals(values[17])) {

                // Name and narrative the same, so clearing narrative
                narrative = "";
            }

            // Begin RefsetJson
            buf.append("{");

            // Populate the Json with values from refsets.txt file
            buf.append("\"name\": \"" + values[17] + "\",");
            buf.append("\"refsetId\": \"" + values[8] + "\",");
            buf.append("\"moduleId\": \"" + values[5] + "\",");
            buf.append("\"version\": \"" + values[2] + "\","); // "2021-05-30 00:00:00"
            buf.append("\"narrative\": \"" + narrative + "\",");
            buf.append("\"privateRefset\": " + ((values[15].equals("0")) ? "true" : "false"));

            // Complete the json
            buf.append("}");

            // Store ability to map from RefsetId to ProjectId
            rttIdToRttProjectIdMap.put(rttRefsetId, values[27]);

            return buf.toString();
        } catch (final Exception e) {

            LOG.error("Failed to process line #" + lineNumber + " of refset json: " + line);

            e.printStackTrace();

            throw e;
        }

    }

    /**
     * Line to project json.
     *
     * @param line the line
     * @param lineNumber the line number
     * @throws Exception the exception
     */
    private void parseProjectLine(final String line, final int lineNumber) throws Exception {

        if (line.toLowerCase().contains(SyncUtilities.DEVELOPER_ORGANIZATION_NAME_KEYWORD)) {

            projectsToIgnore.add(line.split(SPLIT_CHARACTER)[0]);
        }

        try {

            final String[] values = line.split(SPLIT_CHARACTER);

            final String projectName = values[7].replaceAll("\"", "");
            final String projectDescription = values[1];
            String editionShortName = values[9].replaceAll("\"", "");
            String organizationCrowdId = null;

            if (editionShortName.equals(OLD_SNOMED_CORE_NAME)) {

                editionShortName = SyncAgent.SNOMED_CORE_EDITION_NAME;
            }

            if (existingEditionProjectInfo.containsKey(editionShortName) && existingEditionProjectInfo.get(editionShortName).containsKey(projectName)) {

                organizationCrowdId = existingEditionProjectInfo.get(editionShortName).get(projectName);
            }

            if (organizationCrowdId == null || organizationCrowdId.isEmpty()) {

                StringBuffer s = new StringBuffer();

                String[] nameParts = projectName.split(" ");

                for (int i = 0; i < nameParts.length; i++) {

                    s.append(nameParts[i].toLowerCase().charAt(0));
                }

                organizationCrowdId = s.toString();
            }

            SyncProjectMetadata newProject =
                new SyncProjectMetadata(line.split(SPLIT_CHARACTER)[0], organizationCrowdId, projectName, projectDescription, editionShortName);

            projectData.add(newProject);

        } catch (final Exception e) {

            LOG.error("Failed to process line #" + lineNumber + " of project json: " + line);

            e.printStackTrace();

            throw e;
        }

    }

    /**
     * Returns the project id to project info map.
     *
     * @return the project id to project info map
     */
    public Map<String, Map<String, String>> getProjectIdToProjectInfoMap() {

        if (projectIdToProjectInfoMap.isEmpty()) {

            readRttProjectInfo();
        }

        return projectIdToProjectInfoMap;
    }

    /**
     * Returns the sct id to project id map.
     *
     * @return the sct id to project id map
     */
    public Map<String, String> getSctIdToProjectIdMap() {

        if (sctIdToProjectIdMap.isEmpty()) {

            readRttProjectInfo();
        }

        return sctIdToProjectIdMap;
    }

    /**
     * Returns the refset sct to tags map.
     *
     * @return the refset sct to tags map
     */
    public Map<String, Set<String>> getRefsetSctToTagsMap() {

        return refsetSctIdToTagsMap;
    }

    /**
     * Returns the refset sct id to rtt id map.
     *
     * @return the refset sct id to rtt id map
     */
    public Map<String, Set<String>> getRefsetSctIdToRttIdMap() {

        return rttRefsetSctIdToRttIdMap;
    }

    /**
     * Returns the rtt id to refset json map.
     *
     * @return the rtt id to refset json map
     */
    public Map<String, String> getRttIdToRefsetJsonMap() {

        return rttIdToRefsetJsonMap;
    }

    /**
     * Returns the refset sct to clauses map.
     *
     * @return the refset sct to clauses map
     */
    public Map<String, ArrayList<String>> getRefsetSctToClausesMap() {

        return refsetSctIdToClausesMap;
    }

    /**
     * Returns the project data map.
     *
     * @return the project data map
     */
    public Set<SyncProjectMetadata> getProjectData() {

        return projectData;
    }
}
