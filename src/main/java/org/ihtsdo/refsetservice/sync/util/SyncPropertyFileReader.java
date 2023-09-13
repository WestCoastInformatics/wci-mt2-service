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
	private final ClassPathResource refsetRttToSctIdResource = new ClassPathResource(
			"sync/rtt-migration/refsetRttToSct.txt");

	/** The refset to tags resource. */
	private final ClassPathResource refsetToTagsResource = new ClassPathResource("sync/rtt-migration/refsetToTags.txt");

	/** The refset to projects resource. */
	private final ClassPathResource refsetToProjectsResource = new ClassPathResource(
			"sync/rtt-migration/refsetToProjects.txt");

	/** The refset to description resource. */
	private final ClassPathResource refsetToDescriptionResource = new ClassPathResource(
			"sync/rtt-migration/refsetToDescription.txt");

	/** The Constant IGNORED_CODE_SYSTEMS_PATH. */
	private static final String IGNORED_CODE_SYSTEMS_PATH = "sync/exceptions/ignoredCodeSystems.txt";

	/** The ignored code systems resource. */
	private ClassPathResource ignoredCodeSystemsResource = new ClassPathResource(IGNORED_CODE_SYSTEMS_PATH);

	// TODO: JESSE - Review the existing needs for these remaining resources files
	// as they may have been created to deal with bad data
	/** The undefined default lang refsets resource. */
	private final ClassPathResource undefinedDefaultLangRefsetsResource = new ClassPathResource(
			"sync/exceptions/undefinedDefaultLangRefsets.txt");

	/** The team creation resource. */
	private final ClassPathResource teamCreationResource = new ClassPathResource("sync/initial-teams/teamCreation.txt");

	/** The team to project assignment resource. */
	private final ClassPathResource teamToProjectAssignmentResource = new ClassPathResource(
			"sync/initial-teams/teamToProjectAssignment.txt");

	/** The team membership resource. */
	private final ClassPathResource teamMembershipResource = new ClassPathResource(
			"sync/initial-teams/teamMembership.txt");

	/** The existing migrate clean resource. */
	private final ClassPathResource existingMigrateCleanResource = new ClassPathResource(
			"sync/system-migration/existingProjectNameIds.txt");

	/** The Constant SPLIT_CHARACTER. */
	public static final String SPLIT_CHARACTER = "\t";

	private static final String OLD_SNOMED_CORE_NAME = "IHTSDO";

	private static final String NEW_SNOMED_CORE_NAME = "SNOMEDCT";

	/** The refset to description map. */
	private final Map<String, String> refsetToDescriptionMap = readRttRefsetsToDescriptionMap();

	/** The refset sct id to tags map. */
	private final Map<String, Set<String>> refsetSctIdToTagsMap = readRefsetSctIdToTagsMap();

	/** The team creation. */
	private final Map<String, Map<String, Set<String>>> teamCreation = readTeamCreation();

	/** The team to projects. */
	private final Map<String, Set<String>> teamToProjects = readTeamToProjectAssignement();

	/** The team membership. */
	private final Map<String, Set<String>> teamMembership = readTeamMembership();

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

	/** The rtt refset to effective date map. */
	private final Map<String, String> rttRefsetToEffectiveDateMap = new HashMap<>();

	/** The existing edition project info. */
	private final Map<String, Map<String, String>> existingEditionProjectInfo = readExistingEditionProjectInfo();

	/** The metadata map. */
	private final Map<String, SyncPersistenceMetadata> metadataMap = new HashMap<>();

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

		try (final InputStreamReader in = new InputStreamReader(refsetRttToSctIdResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

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
	}

	/**
	 * Returns the code systems to ignore.
	 *
	 * @return the code systems to ignore
	 */
	// Reread every time as can now update list without rebuilding. Not an issue as
	// it's only used via sync (so not costly)
	public List<String> getCodeSystemsToIgnore() {

		ignoredCodeSystemsResource = new ClassPathResource(IGNORED_CODE_SYSTEMS_PATH);
		try (final InputStreamReader in = new InputStreamReader(ignoredCodeSystemsResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

			String line = reader.readLine();

			while (line != null) {

				if (!line.isBlank()) {

					final String shortName = line.split("\t")[0];

					codeSystemShortNames.add(shortName);
				}

				line = reader.readLine();
			}

		} catch (final IOException e) {

			e.printStackTrace();
		}

		return codeSystemShortNames;
	}

	/**
	 * Read rtt refsets to description map.
	 *
	 * @return the map
	 */
	private Map<String, String> readRttRefsetsToDescriptionMap() {

		final Map<String, String> refsetToDescriptionMap = new HashMap<>();

		try (final InputStreamReader in = new InputStreamReader(refsetToDescriptionResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

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

		} catch (final Exception e) {

			e.printStackTrace();
		}

		return refsetToDescriptionMap;
	}

	/**
	 * Read rtt project info.
	 */
	private void readRttProjectInfo() {

		try (final InputStreamReader in = new InputStreamReader(refsetToProjectsResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

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

		try (final InputStreamReader in = new InputStreamReader(refsetToTagsResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

			String line = reader.readLine();

			while (line != null && !line.isEmpty()) {

				final String[] columns = line.split(SPLIT_CHARACTER);

				if (!refsetToTagsInfoMap.containsKey(columns[0])) {

					refsetToTagsInfoMap.put(columns[0], new HashSet<>());
				}

				if (columns.length == 2 && !columns[0].isEmpty() && !columns[1].isEmpty()
						&& refsetToTagsInfoMap.containsKey(columns[0])) {

					refsetToTagsInfoMap.get(columns[0]).add(stripQuotes(columns[1]));
				} else {

					LOG.info("Skipping this line in readRttRefsetsToTagsMap(): " + line);
				}

				line = reader.readLine();
			}

		} catch (final IOException e) {

			e.printStackTrace();
		}

		return refsetToTagsInfoMap;
	}

	/**
	 * Read team creation.
	 *
	 * @return the map
	 */
	private Map<String, Map<String, Set<String>>> readTeamCreation() {

		final Map<String, Map<String, Set<String>>> teamsToCreate = new HashMap<>();

		try (final InputStreamReader in = new InputStreamReader(teamCreationResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

			String line = reader.readLine();

			while (line != null && !line.isEmpty()) {

				final String[] columns = line.split("\t");

				if (columns.length != 3) {

					throw new IOException(
							"line: " + line + " has only " + columns.length + " rather than the expected amount (3)");
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

		} catch (

		final IOException e) {

			e.printStackTrace();
		}

		return teamsToCreate;
	}

	/**
	 * Read team to project assignement.
	 *
	 * @return the map
	 */
	private Map<String, Set<String>> readTeamToProjectAssignement() {

		final Map<String, Set<String>> teamToProjects = new HashMap<>();

		try (final InputStreamReader in = new InputStreamReader(teamToProjectAssignmentResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

			String line = reader.readLine();

			while (line != null && !line.isEmpty()) {

				final String[] columns = line.split("\t");

				if (columns.length != 2) {

					throw new IOException(
							"line: " + line + " has only " + columns.length + " rather than the expected amount (2)");
				}

				if (!teamToProjects.containsKey(columns[0])) {

					teamToProjects.put(columns[0], new HashSet<>());
				}

				teamToProjects.get(columns[0]).add(stripQuotes(columns[1]));
				line = reader.readLine();
			}

		} catch (final IOException e) {

			e.printStackTrace();
		}

		return teamToProjects;
	}

	/**
	 * Read team membership.
	 *
	 * @return the map
	 */
	private Map<String, Set<String>> readTeamMembership() {

		final Map<String, Set<String>> teamMembership = new HashMap<>();

		try (final InputStreamReader in = new InputStreamReader(teamMembershipResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

			String line = reader.readLine();

			while (line != null && !line.isEmpty()) {

				final String[] columns = line.split("\t");

				if (columns.length != 2) {

					throw new IOException(
							"line: " + line + " has only " + columns.length + " rather than the expected amount (2)");
				}

				if (!teamMembership.containsKey(columns[0])) {

					teamMembership.put(columns[0], new HashSet<String>());
				}

				teamMembership.get(columns[0]).add(columns[1]);
				line = reader.readLine();
			}

		} catch (final IOException e) {

			e.printStackTrace();
		}

		return teamMembership;
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

			try (final InputStreamReader in = new InputStreamReader(
					undefinedDefaultLangRefsetsResource.getInputStream());
					final BufferedReader reader = new BufferedReader(in);) {

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

		try (final InputStreamReader in = new InputStreamReader(existingMigrateCleanResource.getInputStream());
				final BufferedReader reader = new BufferedReader(in);) {

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

		} catch (final IOException e) {

			e.printStackTrace();
		}

		return projectInfo;
	}

	/**
	 * Generate json from sql file.
	 *
	 * @param classPathResource the input resource
	 * @param processType       the process type
	 * @throws Exception the exception
	 */
	private void populateFromFile(final ClassPathResource classPathResource, final FileProcessType processType)
			throws Exception {

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

		} catch (final IOException e) {

			e.printStackTrace();
		}

	}

	/**
	 * Returns the test queries.
	 *
	 * @param classPathResource the class path resource
	 * @return the test queries
	 * @throws Exception the exception
	 */
	public List<String> getTestQueries(final ClassPathResource classPathResource) throws Exception {

		final List<String> lines = FileUtils.readLines(new File(classPathResource.getPath()), "utf-8");

		return lines;

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
	 * @param line       the line
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
	 * @param line       the line
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
				updatedLine = updatedLine.substring(0, descStartIdx) + narrative.replaceAll(SPLIT_CHARACTER, "")
						+ updatedLine.substring(descStartIdx + descEndIdx + 2);
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
				updatedLine = updatedLine.substring(0, nameStartIdx) + name.replaceAll(",", "")
						+ updatedLine.substring(nameStartIdx + nameEndIdx + 2);
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
			} else if (values[1].length() != 1
					|| (!values[1].equals("1") && !Character.isISOControl(values[1].charAt(0)))) {

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
	 * @param line       the line
	 * @param lineNumber the line number
	 * @throws Exception the exception
	 */
	private void parseProjectLine(final String line, final int lineNumber) throws Exception {

		String projectName;
		String projectDescription;
		String modified;
		String modifiedBy;
		String editionShortName;
		String crowdId = null;

		if (line.toLowerCase().contains(SyncUtilities.DEVELOPER_ORGANIZATION_NAME_KEYWORD)) {

			projectsToIgnore.add(line.split(SPLIT_CHARACTER)[0]);
		}

		try {

			final String[] values = line.split(SPLIT_CHARACTER);

			projectName = values[7].replaceAll("\"", "");
			projectDescription = values[1];
			editionShortName = values[9].replaceAll("\"", "");
			modified = values[4];
			modifiedBy = values[5];

			if (editionShortName.equals(OLD_SNOMED_CORE_NAME)) {

				editionShortName = NEW_SNOMED_CORE_NAME;
			}

			if (existingEditionProjectInfo.containsKey(editionShortName)
					&& existingEditionProjectInfo.get(editionShortName).containsKey(projectName)) {

				crowdId = existingEditionProjectInfo.get(editionShortName).get(projectName);
			}

			// TODO: TESTING - Create new project on crowd (without users but that gets
			// added with project=ALL
			if (crowdId == null || crowdId.isEmpty()) {

				StringBuffer s = new StringBuffer();

				String[] nameParts = projectName.split(" ");

				for (int i = 0; i < nameParts.length; i++) {

					s.append(nameParts[i].toLowerCase().charAt(0));
				}

				crowdId = s.toString();
			}

			SyncProjectMetadata newProject = new SyncProjectMetadata(line.split(SPLIT_CHARACTER)[0], crowdId,
					projectName, projectDescription, editionShortName);

			projectData.add(newProject);

			metadataMap.put("project-" + line.split(SPLIT_CHARACTER)[0],
					new SyncPersistenceMetadata(modified, modifiedBy));
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
	 * Returns the refset to description map.
	 *
	 * @return the refset to description map
	 */
	public Map<String, String> getRefsetToDescriptionMap() {

		return refsetToDescriptionMap;
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
	 * Returns the rtt refset to effective date map.
	 *
	 * @return the rtt refset to effective date map
	 */
	public Map<String, String> getRttRefsetToEffectiveDateMap() {

		return rttRefsetToEffectiveDateMap;
	}

	/**
	 * Returns the metadata map.
	 *
	 * @return the metadata map
	 */
	public Map<String, SyncPersistenceMetadata> getMetadataMap() {

		return metadataMap;
	}

	/**
	 * Returns the team creation.
	 *
	 * @return the team creation
	 */
	public Map<String, Map<String, Set<String>>> getTeamCreation() {

		return teamCreation;
	}

	/**
	 * Returns the team to projects.
	 *
	 * @return the team to projects
	 */
	public Map<String, Set<String>> getTeamToProjects() {

		return teamToProjects;
	}

	/**
	 * Returns the team membership.
	 *
	 * @return the team membership
	 */
	public Map<String, Set<String>> getTeamMembership() {

		return teamMembership;
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
