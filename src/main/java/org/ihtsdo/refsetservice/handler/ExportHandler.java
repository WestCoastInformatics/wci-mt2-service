/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.handler;

import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
//import org.flywaydb.core.internal.license.Edition;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;

/**
 * Generically represents an algorithm for object searching.
 */
/**
 * @author jesseefron
 *
 */
public class ExportHandler {

	/** The Constant TOP_LEVEL_AWS_FOLDER. */
	private static final String TOP_LEVEL_AWS_FOLDER = S3ConnectionWrapper.getProjectDirectory() + "/";

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(ExportHandler.class);

	/** The config properties. */
	protected static final Properties PROPERTIES = PropertyUtility.getProperties();

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

			terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName,
					TerminologyServerHandler.class);

		} catch (Exception e) {
			LOG.error("Failed to initialize terminology.handler - serious error", e);
			terminologyHandler = null;
		}
	}

	/**
	 * Indicates whether or not requested file on S 3 is the case.
	 *
	 * @param refset   the refset
	 * @param s3Client the s 3 client
	 * @param type     the type
	 * @param dates    the dates
	 * @return <code>true</code> if so, <code>false</code> otherwise
	 * @throws Exception the exception
	 */
	public boolean isRequestedFileOnS3(final Refset refset, final AmazonS3 s3Client, final String type,
			final Set<String> dates) throws Exception {

		// Check if date lives on s3
		return false;
	}

	/**
	 * Generate RT2 version file name.
	 *
	 * @param refset         the refset
	 * @param type           the type
	 * @param languageId     the language id
	 * @param dates          the dates
	 * @param exportMetadata the export metadata
	 * @param withNames      the with names
	 * @return the string
	 * @throws Exception the exception
	 */
	public String generateRt2VersionFileName(final Refset refset, final String type, final String languageId,
			final Set<String> dates, final boolean exportMetadata, final boolean withNames) throws Exception {

		// RT2-1037
		// der2_Refset_<refset_name_in_camelcase><Snapshot|Delta><CountryCode><Namespace>_<PublicationDate>.txt

		if ((type.toLowerCase().contains("snapshot") && dates.size() != 1)
				// if (("snapshot".equals(type.toLowerCase()) &&
				// transientEffectiveTime != null)
				|| ("delta".equals(type.toLowerCase()) && dates.size() != 2)) {
			throw new Exception("Have a " + type + " rf2 request with " + dates.size() + " number of dates provided");
		}

		String namespace = "";
		try (final TerminologyService service = new TerminologyService()) {

			final String editionId = refset.getEditionId();
			final Edition edition = service.get(editionId, Edition.class);
			namespace = edition.getNamespace() != null ? edition.getNamespace() : "";
		}

		String countryCode = refset.getEditionShortName().replace("SNOMEDCT", "").replace("-", "");
		if (StringUtils.isBlank(countryCode)) {
			countryCode = "INT";
		}

		String name;

		if ("snapshot".equals(type.toLowerCase())) {

			name = "der2_Refset_" + StringUtility.camelCase(refset.getName().replaceAll("[\\\\/:*?\"<>|]", "-"))
					+ type.toUpperCase() + "_" + countryCode + namespace + refset.getRefsetId() + "_"
					+ dates.toArray()[0];

		} else {

			name = "der2_Refset_" + StringUtility.camelCase(refset.getName().replaceAll("[\\\\/:*?\"<>|]", "-"))
					+ type.toUpperCase() + "_" + countryCode + namespace + refset.getRefsetId() + "_"
					+ dates.toArray()[0] + "_" + (dates.toArray().length > 1 ? dates.toArray()[1] : dates.toArray()[0]);
		}

		if (withNames) {
			name = name + "_" + languageId; // TODO: Add Language here too
		}

		if (exportMetadata) {
			name = name + "_With-Metadata"; // TODO: Add Language here too
		}

		name = name + ".zip";

		return name;
	}

	/**
	 * Generate aws base version path.
	 *
	 * @param refset the refset
	 * @param type   the type
	 * @param dates  the dates
	 * @return the string
	 * @throws Exception the exception
	 */
	public String generateAwsBaseVersionPath(final Refset refset, final String type, final Set<String> dates)
			throws Exception {

		String path = getAwsBranchPath(refset) + "/" + refset.getRefsetId() + "/" + dates.toArray()[0] + "/" + type;

		if (!"snapshot".equals(type.toLowerCase())) {
			path += "/" + (dates.toArray().length > 1 ? dates.toArray()[1] : dates.toArray()[0]);
		}

		return path;
	}

	/**
	 * Returns the aws branch path.
	 *
	 * @param refset the refset
	 * @return the aws branch path
	 * @throws Exception the exception
	 */
	public String getAwsBranchPath(final Refset refset) throws Exception {

		return TOP_LEVEL_AWS_FOLDER + RefsetService.getBranchPath(refset);
	}

	/**
	 * Generate snow version file name.
	 *
	 * @param refset the refset
	 * @param type   the type
	 * @param dates  the dates
	 * @return the string
	 */
	public String generateSnowVersionFileName(final Refset refset, final String type, final Set<String> dates) {

		// der2_Refset_Simple551000172106Snapshot_BE_20200315
		if ("snapshot".equals(type.toLowerCase())) {
			return "der2_Refset_Simple" + refset.getRefsetId() + type.toUpperCase() + "_" + refset.getEditionShortName()
					+ "_" + dates.toArray()[0] + ".zip";
		} else {
			return "der2_Refset_Simple" + refset.getRefsetId() + type.toUpperCase() + "_" + refset.getEditionShortName()
					+ "_" + dates.toArray()[0] + "_"
					+ (dates.toArray().length > 1 ? dates.toArray()[1] : dates.toArray()[0]) + ".txt";
		}
	}

	/**
	 * Returns the top level aws path.
	 *
	 * @return the top level aws path
	 */
	public String getTopLevelAwsPath() {

		return TOP_LEVEL_AWS_FOLDER;
	}

	/**
	 * Delete files from branch path.
	 *
	 * @param branchPath the branch path
	 * @return true, if successful
	 * @throws Exception the exception
	 */
	public boolean deleteFilesFromBranchPath(final String branchPath) throws Exception {

		return S3ConnectionWrapper.deleteObjectFromAws(getTopLevelAwsPath() + branchPath);
	}

	/**
	 * Generate snow version file.
	 *
	 * @param entityString the entity string
	 * @return the string
	 * @throws Exception the exception
	 */
	public String generateSnowVersionFile(final String entityString) throws Exception {

		return terminologyHandler.generateVersionFile(entityString);

	}

	/**
	 * Download snow generated file.
	 *
	 * @param snowVersionFileUrl   the snow version file url
	 * @param localSnowVersionPath the local snow version path
	 * @throws Exception the exception
	 */
	public void downloadSnowGeneratedFile(final String snowVersionFileUrl, final String localSnowVersionPath)
			throws Exception {

		terminologyHandler.downloadGeneratedFile(snowVersionFileUrl, localSnowVersionPath);
	}

	/**
	 * Generate MT2 version file name.
	 *
	 * @param refset         the refset
	 * @param type           the type
	 * @param languageId     the language id
	 * @param dates          the dates
	 * @param exportMetadata the export metadata
	 * @param withNames      the with names
	 * @return the string
	 * @throws Exception the exception
	 */
	public String generateMt2VersionFileName(final MapProject mapProject, MapSet mapset, final String type,
			final String languageId, final String transientEffectiveTime, final String startEffectiveTime,
			final boolean exportMetadata, final boolean withNames) throws Exception {

		if (StringUtils.isBlank(transientEffectiveTime)) {
			throw new Exception("Transient Effective Time is required for MT2 export");
		}

		if ("delta".equals(type.toLowerCase()) && StringUtils.isAnyBlank(transientEffectiveTime, startEffectiveTime)) {
			throw new Exception("Have a " + type + " rf2 request with startEffectiveTime number of dates provided");
		}

		String name;

		if ("snapshot".equals(type.toLowerCase())) {
			name = "der2_iisssccRefset" + mapProject.getDestinationTerminology() + "ExtendedMap"
					+ mapset.getRefSetCode() + type.toUpperCase() + "_"
					+ mapProject.getEdition().getAbbreviation().toUpperCase() + "_" + transientEffectiveTime;
		} else {
			name = "der2_iisssccRefset" + mapProject.getDestinationTerminology() + "ExtendedMap"
					+ mapset.getRefSetCode() + type.toUpperCase() + "_"
					+ mapProject.getEdition().getAbbreviation().toUpperCase() + "_" + transientEffectiveTime + "_"
					+ (StringUtils.isNotBlank(startEffectiveTime) ? startEffectiveTime : transientEffectiveTime);
		}

		if (withNames) {
			name = name + "_" + languageId; // TODO: Add Language here too
		}

		if (exportMetadata) {
			name = name + "_With-Metadata"; // TODO: Add Language here too
		}

		name = name + ".zip";

		LOG.info("ExportHandler generateMt2VersionFileName - " + name);
		return name;
	}

	/**
	 * Generate aws base version path.
	 * 
	 * @param branch the branch
	 * @param mapset the mapset
	 * @param type   the type
	 * @param dates  the dates
	 * @return the string
	 * @throws Exception the exception
	 */
	public String generateAwsMt2BaseVersionPath(final String branch, final MapSet mapset, final String type,
			final String transientEffectiveTime, final String startEffectiveTime) throws Exception {

		final String awsProjectBaseDir = PROPERTIES.getProperty("AWS_PROJECT_BASE_DIR");

		// AWS path check - TODO
		String path = awsProjectBaseDir + "/" + branch + "/" + mapset.getRefSetCode() + "/" + transientEffectiveTime
				+ "/" + type.toUpperCase();
		if (!"snapshot".equals(type.toLowerCase())) {
			path += "/" + (StringUtils.isNotBlank(startEffectiveTime) ? startEffectiveTime : transientEffectiveTime);
		}

		LOG.info("ExportHandler generateAwsMt2BaseVersionPath - " + path);
		return path;
	}

	/**
	 * Generate snow version file name.
	 * 
	 * @param mapProject the mapProject
	 * @param mapset     the mapset
	 * @param type       the type
	 * @param dates      the dates
	 * @return the string
	 */
	public String generateMt2SnowVersionFileName(final MapProject mapProject, final MapSet mapset, final String type,
			final String transientEffectiveTime, final String startEffectiveTime) {

		if ("snapshot".equals(type.toLowerCase())) {
			return "der2_iisssccRefset" + mapProject.getDestinationTerminology() + "ExtendedMap"
					+ mapset.getRefSetCode() + type.toUpperCase() + "_"
					+ mapProject.getEdition().getAbbreviation().toUpperCase() + "_" + transientEffectiveTime + ".zip";
		} else {
			return "der2_iisssccRefset" + mapProject.getDestinationTerminology() + "ExtendedMap"
					+ mapset.getRefSetCode() + type.toUpperCase() + "_"
					+ mapProject.getEdition().getAbbreviation().toUpperCase() + "_" + transientEffectiveTime + "_"
					+ (StringUtils.isNotBlank(startEffectiveTime) ? startEffectiveTime : transientEffectiveTime)
					+ ".zip";
		}
	}

}
