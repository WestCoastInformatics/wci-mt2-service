package org.ihtsdo.refsetservice.sync.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class SyncPropertyFileReader {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncPropertyFileReader.class);

    private final ClassPathResource projectsResource = new ClassPathResource("sync/rtt-migration/projects.txt");

    private final ClassPathResource clausesResource = new ClassPathResource("sync/rtt-migration/clauses.txt");

    private final ClassPathResource refsetsResource = new ClassPathResource("sync/rtt-migration/refsets.txt");

    private final ClassPathResource refsetRttToSctIdResource = new ClassPathResource("sync/rtt-migration/refsetRttToSct.txt");

    private final ClassPathResource refsetToTagsResource = new ClassPathResource("sync/rtt-migration/refsetToTags.txt");

    private final ClassPathResource refsetToProjectsResource = new ClassPathResource("sync/rtt-migration/refsetToProjects.txt");

    private final ClassPathResource refsetToDescriptionResource = new ClassPathResource("sync/rtt-migration/refsetToDescription.txt");

    private static final String IGNORED_CODE_SYSTEMS_PATH = "sync/exceptions/ignoredCodeSystems.txt";

    private ClassPathResource ignoredCodeSystemsResource = new ClassPathResource(IGNORED_CODE_SYSTEMS_PATH);

    private final ClassPathResource ignoredRefsetsResource = new ClassPathResource("sync/exceptions/ignoredRefsets.txt");

    private final ClassPathResource undefinedDefaultLangRefsetsResource = new ClassPathResource("sync/exceptions/undefinedDefaultLangRefsets.txt");

    private final ClassPathResource teamCreationResource = new ClassPathResource("sync/initial-teams/teamCreation.txt");

    private final ClassPathResource teamToProjectAssignmentResource = new ClassPathResource("sync/initial-teams/teamToProjectAssignment.txt");

    private final ClassPathResource teamMembershipResource = new ClassPathResource("sync/initial-teams/teamMembership.txt");

    /** The Constant SPLIT_CHARACTER. */
    public static final String SPLIT_CHARACTER = "\t";

    private final Map<String, String> refsetToDescriptionMap = readRttRefsetsToDescriptionMap();

    private final Map<String, Set<String>> refsetSctIdToTagsMap = readRefsetSctIdToTagsMap();

    private final Map<String, Map<String, Set<String>>> teamCreation = readTeamCreation();

    private final Map<String, Set<String>> teamToProjects = readTeamToProjectAssignement();

    private final Map<String, Set<String>> teamMembership = readTeamMembership();

    private final List<String> codeSystemShortNames = new ArrayList<>();

    /** The refset internal id map. */
    private final Map<String, String> rttIdToRefsetJsonMap = new HashMap<>();

    /** The refset sct id to internal id map. */
    private final Map<String, Set<String>> rttRefsetSctIdToRttIdMap = new HashMap<>();

    /** The rtt refset to clauses map. */
    private final Map<String, ArrayList<String>> refsetSctIdToClausesMap = new HashMap<>();

    private final Map<String, String> projectOrganizationMap = new HashMap<>();

    private final Set<String> projectsToIgnore = new HashSet<>();

    /** The refset to project map. */
    private final Map<String, String> rttIdToRttProjectIdMap = new HashMap<>();

    private final Map<String, String> rttRefsetToEffectiveDateMap = new HashMap<>();

    /** The metadata map. */
    private final Map<String, SyncPersistenceMetadata> metadataMap = new HashMap<>();

    private final Map<String, String> sctIdToProjectIdMap = new HashMap<>();

    /** Map of Project ids to map of project name-to-description */
    private final Map<String, Map<String, String>> projectIdToProjectInfoMap = new HashMap<>();

    private static Map<String, Set<String>> defaultLanguageRefsetMap = null;

    private static List<String> refsetsToIgnore = null;

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
     * @return
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

    public List<String> getRefsetsToIgnore() {

        if (refsetsToIgnore == null) {

            refsetsToIgnore = new ArrayList<>();

            try {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(ignoredRefsetsResource.getInputStream()));

                String line = reader.readLine();

                while (line != null) {

                    refsetsToIgnore.add(line);

                    line = reader.readLine();
                }

                reader.close();
            } catch (final IOException e) {

                e.printStackTrace();
            }

        }

        return refsetsToIgnore;
    }


    private Map<String, String> readRttRefsetsToDescriptionMap() {

        final Map<String, String> refsetToDescriptionMap = new HashMap<>();

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(refsetToDescriptionResource.getInputStream()));

            // Grab header first
            String line = reader.readLine();
            line = reader.readLine();

            while (line != null && !line.trim().isEmpty()) {

                final int columnSplit = line.indexOf(SyncPropertyFileReader.SPLIT_CHARACTER);

                if (columnSplit < 0) {

                    LOG.error("Have issue with line: " + line);

                }

                final String sctId = line.substring(0, columnSplit);
                final String description = stripQuotes(line.substring(columnSplit + 1));

                refsetToDescriptionMap.put(sctId, description);

                line = reader.readLine();
            }

            reader.close();
        } catch (final Exception e) {

            e.printStackTrace();
        }

        return refsetToDescriptionMap;
    }

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

    private Map<String, Map<String, Set<String>>> readTeamCreation() {

        final Map<String, Map<String, Set<String>>> teamsToCreate = new HashMap<>();

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(teamCreationResource.getInputStream()));

            String line = reader.readLine();

            while (line != null && !line.isEmpty()) {

                final String[] columns = line.split("\t");

                if (columns.length != 3) {

                    throw new IOException("line: " + line + " has only " + columns.length + " rather than the expected amount (3)");
                }

                if (!teamsToCreate.containsKey(columns[0])) {

                    teamsToCreate.put(columns[0], new HashMap<String, Set<String>>());
                }

                if (!teamsToCreate.get(columns[0]).containsKey(columns[1])) {

                    teamsToCreate.get(columns[0]).put(columns[1], new HashSet<String>());
                }

                teamsToCreate.get(columns[0]).get(columns[1]).add(columns[2]);
                line = reader.readLine();
            }

            reader.close();
        } catch (

        final IOException e) {

            e.printStackTrace();
        }

        return teamsToCreate;
    }

    private Map<String, Set<String>> readTeamToProjectAssignement() {

        final Map<String, Set<String>> teamToProjects = new HashMap<>();

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(teamToProjectAssignmentResource.getInputStream()));

            String line = reader.readLine();

            while (line != null && !line.isEmpty()) {

                final String[] columns = line.split("\t");

                if (columns.length != 2) {

                    throw new IOException("line: " + line + " has only " + columns.length + " rather than the expected amount (2)");
                }

                if (!teamToProjects.containsKey(columns[0])) {

                    teamToProjects.put(columns[0], new HashSet<>());
                }

                teamToProjects.get(columns[0]).add(stripQuotes(columns[1]));
                line = reader.readLine();
            }

            reader.close();
        } catch (final IOException e) {

            e.printStackTrace();
        }

        return teamToProjects;
    }

    private Map<String, Set<String>> readTeamMembership() {

        final Map<String, Set<String>> teamMembership = new HashMap<>();

        try {

            final BufferedReader reader = new BufferedReader(new InputStreamReader(teamMembershipResource.getInputStream()));

            String line = reader.readLine();

            while (line != null && !line.isEmpty()) {

                final String[] columns = line.split("\t");

                if (columns.length != 2) {

                    throw new IOException("line: " + line + " has only " + columns.length + " rather than the expected amount (2)");
                }

                if (!teamMembership.containsKey(columns[0])) {

                    teamMembership.put(columns[0], new HashSet<String>());
                }

                teamMembership.get(columns[0]).add(columns[1]);
                line = reader.readLine();
            }

            reader.close();
        } catch (final IOException e) {

            e.printStackTrace();
        }

        return teamMembership;
    }

    Map<String, Set<String>> readUndefinedDefaultLanguageRefsets() {

        if (defaultLanguageRefsetMap == null) {

            defaultLanguageRefsetMap = new HashMap<>();

            try {

                final BufferedReader reader = new BufferedReader(new InputStreamReader(undefinedDefaultLangRefsetsResource.getInputStream()));

                String line = reader.readLine();

                while (line != null) {

                    final String[] columns = line.split("\t");
                    defaultLanguageRefsetMap.put(columns[0], new HashSet<String>());

                    for (int i = 1; i < columns.length; i++) {

                        defaultLanguageRefsetMap.get(columns[0]).add(columns[i]);
                    }

                    line = reader.readLine();
                }

                reader.close();
            } catch (final IOException e) {

                e.printStackTrace();
            }

        }

        return defaultLanguageRefsetMap;
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

    public List<String> getTestQueries(final ClassPathResource classPathResource) throws Exception {

        final List<String> lines = FileUtils.readLines(new File(classPathResource.getPath()), "utf-8");

        return lines;

    }

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
     * @return the string
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
                updatedLine = updatedLine.substring(0, descStartIdx) + narrative.replaceAll(SPLIT_CHARACTER, "") + updatedLine.substring(descStartIdx + descEndIdx + 2);
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
            final String values[] = updatedLine.split(SPLIT_CHARACTER);

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

            // Store effective Time to avoid handling it within Json
            rttRefsetToEffectiveDateMap.put(rttRefsetId, values[2]);

            final SyncPersistenceMetadata meta = new SyncPersistenceMetadata(values[3], values[4]);
            metadataMap.put("refset-" + rttRefsetId, meta);

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
     * @return the string
     * @throws Exception the exception
     */
    private void parseProjectLine(final String line, final int lineNumber) throws Exception {

        String organizationName;
        String modified;
        String modifiedBy;

        if (line.toLowerCase().contains(SyncUtilities.DEVELOPER_ORGANIZATION_NAME_KEYWORD)) {

            projectsToIgnore.add(line.split(SPLIT_CHARACTER)[0]);
        }

        try {
            if (line.split(SPLIT_CHARACTER)[1].startsWith("\"")) {

                // If description has commas (and some do), can't rely on
                // splitting
                // on comma. Must identify Description and then remove from line
                // before finding other values
                final int descStartIdx = line.indexOf("\"");
                final int descEndIdx = line.substring(descStartIdx + 1).indexOf("\"");
                final String[] values = line.substring(descStartIdx + descEndIdx + 3).split(SPLIT_CHARACTER);

                organizationName = values[7].replaceAll("\"", "");
                modified = values[2];
                modifiedBy = values[3];
            } else {

                final String[] values = line.split(SPLIT_CHARACTER);

                organizationName = values[9].replaceAll("\"", "");
                modified = values[4];
                modifiedBy = values[5];
            }

            projectOrganizationMap.put(line.split(SPLIT_CHARACTER)[0], organizationName);

            metadataMap.put("project-" + line.split(SPLIT_CHARACTER)[0], new SyncPersistenceMetadata(modified, modifiedBy));
        } catch (final Exception e) {

            LOG.error("Failed to process line #" + lineNumber + " of project json: " + line);

            e.printStackTrace();

            throw e;
        }

    }

    public Map<String, Map<String, String>> getProjectIdToProjectInfoMap() {

        if (projectIdToProjectInfoMap.isEmpty()) {
            readRttProjectInfo();
        }

        return projectIdToProjectInfoMap;
    }

    public Map<String, String> getSctIdToProjectIdMap() {

        if (sctIdToProjectIdMap.isEmpty()) {
            readRttProjectInfo();
        }

        return sctIdToProjectIdMap;
    }


    Map<String, String> getRefsetToDescriptionMap() {

        return refsetToDescriptionMap;
    }

    public Map<String, Set<String>> getRefsetSctToTagsMap() {

        return refsetSctIdToTagsMap;
    }

    public Map<String, Set<String>> getRefsetSctIdToRttIdMap() {

        return rttRefsetSctIdToRttIdMap;
    }

    public Map<String, String> getRttIdToRefsetJsonMap() {

        return rttIdToRefsetJsonMap;
    }

    public Map<String, ArrayList<String>> getRefsetSctToClausesMap() {

        return refsetSctIdToClausesMap;
    }

    Map<String, String> getRttRefsetToEffectiveDateMap() {

        return rttRefsetToEffectiveDateMap;
    }

    public Map<String, SyncPersistenceMetadata> getMetadataMap() {

        return metadataMap;
    }

    public Map<String, Map<String, Set<String>>> getTeamCreation() {

        return teamCreation;
    }

    public Map<String, Set<String>> getTeamToProjects() {

        return teamToProjects;
    }

    public Map<String, Set<String>> getTeamMembership() {

        return teamMembership;
    }

    Map<String, String> getProjectOrganizationMap() {

        return projectOrganizationMap;
    }
}
