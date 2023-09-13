/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Utility class for interacting with files.
 */
public final class FileUtility {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(FileUtility.class);

	/** Size of the buffer to read/write data. */
	private static final int BUFFER_SIZE = 4096;

	/** The local icon file directory. */
	private static String serverIconDir;

	/** The local artifact file directory. */
	private static String serverArtifactDir;

	/** Static initialization. */
	static {

		serverIconDir = PropertyUtility.getProperty("refset.service.icon.server.dir");
		serverArtifactDir = PropertyUtility.getProperty("refset.service.artifact.server.dir");
	}

	/**
	 * Instantiates an empty {@link FileUtility}.
	 */
	private FileUtility() {

		// n/a
	}

	/**
	 * Extracts a zip file specified by the zipFilePath to a directory specified by
	 * destDirectory (will be created if does not exists).
	 *
	 * @param zipFilePath   the zip file path
	 * @param destDirectory the dest directory
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void unzip(final String zipFilePath, final String destDirectory) throws IOException {

		final File destDir = new File(destDirectory);
		if (!destDir.exists()) {
			destDir.mkdir();
		}
		try (final ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
			ZipEntry entry = zipIn.getNextEntry();
			// iterates over entries in the zip file
			while (entry != null) {
				final String filePath = destDirectory + File.separator + entry.getName();
				final File parentDir = new File(filePath).getParentFile();
				if (!parentDir.exists()) {
					parentDir.mkdirs();
				}
				if (!entry.isDirectory()) {
					// if the entry is a file, extracts it
					extractFile(zipIn, filePath);
				} else {
					// if the entry is a directory, make the directory
					final File dir = new File(filePath);
					dir.mkdir();
				}
				zipIn.closeEntry();
				entry = zipIn.getNextEntry();
			}
		}
	}

	/**
	 * Unzip.
	 *
	 * @param in            the in
	 * @param destDirectory the dest directory
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void unzip(final InputStream in, final String destDirectory) throws IOException {

		final File destDir = new File(destDirectory);
		if (!destDir.exists()) {
			destDir.mkdir();
		}
		try (final ZipInputStream zipIn = new ZipInputStream(in)) {
			ZipEntry entry = zipIn.getNextEntry();
			// iterates over entries in the zip file
			while (entry != null) {
				final String filePath = destDirectory + File.separator + entry.getName();
				final File parentDir = new File(filePath).getParentFile();
				if (!parentDir.exists()) {
					parentDir.mkdirs();
				}
				if (!entry.isDirectory()) {
					// if the entry is a file, extracts it
					extractFile(zipIn, filePath);
				} else {
					// if the entry is a directory, make the directory
					final File dir = new File(filePath);
					dir.mkdir();
				}
				zipIn.closeEntry();
				entry = zipIn.getNextEntry();
			}
		}
	}

	/**
	 * Zip.
	 *
	 * @param dirPath the dir path
	 * @throws Exception the exception
	 */
	public static void zip(final String dirPath) throws Exception {

		final Path sourceDir = Paths.get(dirPath);
		final String zipFileName = dirPath.concat(".zip");
		try (final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(zipFileName));) {
			Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes) {

					try {
						final Path targetFile = sourceDir.relativize(file);
						outputStream.putNextEntry(new ZipEntry(targetFile.toString()));
						final byte[] bytes = Files.readAllBytes(file);
						outputStream.write(bytes, 0, bytes.length);
						outputStream.closeEntry();
					} catch (final IOException e) {
						e.printStackTrace();
					}
					return FileVisitResult.CONTINUE;
				}
			});
			outputStream.close();
		}
	}

	/**
	 * Extracts a zip entry (file entry).
	 *
	 * @param zipIn    the zip in
	 * @param filePath the file path
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static void extractFile(final ZipInputStream zipIn, final String filePath) throws IOException {

		try (final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
			final byte[] bytesIn = new byte[BUFFER_SIZE];
			int read = 0;
			while ((read = zipIn.read(bytesIn)) != -1) {
				bos.write(bytesIn, 0, read);
			}
		}
	}

	/**
	 * Resolve uri.
	 *
	 * @param uri the uri
	 * @return the string
	 * @throws Exception the exception
	 */
	public static String resolveUri(final String uri) throws Exception {

		if (uri.startsWith("classpath:")) {
			final String fixuri = uri.replaceFirst("classpath:", "");
			try (final InputStream in = FileUtility.class.getClassLoader()
					.getResourceAsStream(uri.replaceFirst("classpath:", ""));) {
				if (in == null) {
					throw new Exception("UNABLE to find classpath uri = " + fixuri);
				}
				return IOUtils.toString(in, "UTF-8");
			}
		} else {
			try (final InputStream in = new URL(uri).openStream()) {
				return IOUtils.toString(new BufferedInputStream(in), "UTF-8");
			}
		}
	}

	/**
	 * Returns the base filename.
	 *
	 * @param filename the filename
	 * @return the base filename
	 */
	public static String getBaseFilename(final String filename) {

		// The \\\\$ is to handle regex based filenames for the doc service
		// derivative stuff
		return filename.replaceAll("(.*)\\.[A-Za-z0-9]+$", "$1").replaceAll("\\\\$", "");
	}

	/**
	 * Returns the file extension.
	 *
	 * @param filename the filename
	 * @return the file extension
	 */
	public static String getFileExtension(final String filename) {

		String fileExtension = "";
		if (filename.matches(".*\\.[A-Za-z0-9]+$")) {
			fileExtension = filename.replaceAll(".*\\.([A-Za-z0-9]+)$", "$1");
		}

		return (StringUtils.hasText(fileExtension)) ? fileExtension : "";
	}

	/**
	 * Generate a list of line strings from a file removing empty lines.
	 *
	 * @param inputFile the input file
	 * @return the line string List
	 * @throws Exception the exception
	 */
	public static List<String> readFileToArray(final String inputFile) throws Exception {

		final List<String> lineArray = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {

			String line;

			while ((line = reader.readLine()) != null) {
				lineArray.add(line);
			}
		}

		return lineArray;
	}

	/**
	 * Generate a list of line strings from a multipart file removing empty lines.
	 *
	 * @param inputFile the input file
	 * @return the line string List
	 * @throws Exception the exception
	 */
	public static List<String> readFileToArray(final MultipartFile inputFile) throws Exception {

		final List<String> lineArray = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputFile.getInputStream()))) {

			String line;

			while ((line = reader.readLine()) != null) {
				lineArray.add(line);
			}
		}

		return lineArray;
	}

	/**
	 * Move.
	 *
	 * @param sourceFilePath the source file path
	 * @param targetFilePath the target file path
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void move(final String sourceFilePath, final String targetFilePath) throws IOException {

		Files.move(Paths.get(sourceFilePath), Paths.get(targetFilePath), StandardCopyOption.ATOMIC_MOVE);
	}

	/**
	 * Delete directory.
	 *
	 * @param directory the directory
	 * @throws Exception the exception
	 */
	public static void deleteDirectory(final File directory) throws Exception {

		FileUtils.deleteDirectory(directory);

		if (directory.exists()) {
			throw new Exception("Failed to delete the temporary directory: " + directory.getAbsolutePath());
		}
	}

	/**
	 * Returns an icon file from the local disk, loading it from S3 if it is not
	 * found locally.
	 *
	 * @param fileName the file name
	 * @return the file
	 * @throws Exception the exception
	 */
	public static Resource getIconFile(final String fileName) throws Exception {

		final Resource file = getCachedFile(fileName, serverIconDir, S3ConnectionWrapper.getAwsIconPath());

		return file;
	}

	/**
	 * Saves an icon file to the local disk and to S3.
	 *
	 * @param inputFile        the multipart file to save
	 * @param fileNamePrefix   the prefix of the file name before the timestamp
	 * @param fileNameToDelete the file name of a previous version of the file to be
	 *                         deleted from the local disk and S3, or null or empty
	 *                         if no delete is to be performed
	 * @return the file
	 * @throws Exception the exception
	 */
	public static File saveIconFile(final MultipartFile inputFile, final String fileNamePrefix,
			final String fileNameToDelete) throws Exception {

		if (inputFile == null) {
			throw new Exception("Icon file is null.  File name is " + fileNamePrefix);
		}

		final String originalFilename = inputFile.getOriginalFilename();

		if (StringUtils.hasText(originalFilename)) {

			final String extension = getFileExtension(StringUtils.cleanPath(originalFilename)).toLowerCase();
			final String fileName = fileNamePrefix + "-" + (System.currentTimeMillis() / 1000L) + "." + extension;
			final int maxFileSize = Integer.valueOf(PropertyUtility.getProperty("refset.service.icon.file.maxsize"));
			final List<String> fileTypes = Arrays
					.asList(PropertyUtility.getProperty("refset.service.icon.file.types").split(";"));

			final File file = saveCachedFile(inputFile, fileName, serverIconDir, S3ConnectionWrapper.getAwsIconPath(),
					maxFileSize, fileTypes, fileNameToDelete);

			return file;
		}

		return null;
	}

	/**
	 * Returns an artifact file from the local disk, loading it from S3 if it is not
	 * found locally.
	 *
	 * @param fileName the file name
	 * @return the file
	 * @throws Exception the exception
	 */
	public static Resource getArtifactFile(final String fileName) throws Exception {

		final Resource file = getCachedFile(fileName, serverArtifactDir, S3ConnectionWrapper.getAwsArtifactPath());

		return file;
	}

	/**
	 * Saves an artifact file to the local disk and to S3.
	 *
	 * @param inputFile        the multipart file to save
	 * @param fileNamePrefix   the prefix of the file name before the timestamp
	 * @param fileNameToDelete the file name of a previous version of the file to be
	 *                         deleted from the local disk and S3, or null or empty
	 *                         if no delete is to be performed
	 * @return the file
	 * @throws Exception the exception
	 */
	public static File saveArtifactFile(final MultipartFile inputFile, final String fileNamePrefix,
			final String fileNameToDelete) throws Exception {

		if (inputFile == null) {
			throw new Exception("Artifact file is null.  File name is " + fileNamePrefix);
		}

		final String originalFilename = inputFile.getOriginalFilename();

		if (StringUtils.hasText(originalFilename)) {
			final String extension = getFileExtension(originalFilename).toLowerCase();
			final String fileName = fileNamePrefix + "-" + (System.currentTimeMillis() / 1000L) + "." + extension;
			final int maxFileSize = -1;
			final List<String> fileTypes = new ArrayList<>();

			final File file = saveCachedFile(inputFile, fileName, serverArtifactDir,
					S3ConnectionWrapper.getAwsArtifactPath(), maxFileSize, fileTypes, fileNameToDelete);

			return file;
		}
		return null;
	}

	/**
	 * Returns a file from the local disk, loading it from S3 if it is not found
	 * locally.
	 *
	 * @param fileName           the file name
	 * @param localDirectoryPath the local directory path
	 * @param awsDirectoryPath   the AWS directory path
	 * @return the file
	 * @throws Exception the exception
	 */
	public static Resource getCachedFile(final String fileName, final String localDirectoryPath,
			final String awsDirectoryPath) throws Exception {

		final Path localFilePath = Paths.get(localDirectoryPath + File.separator + fileName);
		final Resource file = new UrlResource(localFilePath.toUri());

		LOG.debug("getCachedFile localFilePath: " + localFilePath);

		if (!file.exists() || !file.isReadable()) {

			LOG.debug("getCachedFile awsDirectoryPath: " + awsDirectoryPath + " ; fileName: " + fileName);

			if (S3ConnectionWrapper.isInS3Cache(awsDirectoryPath, fileName)) {

				S3ConnectionWrapper.downloadFileFromS3(awsDirectoryPath, fileName, localFilePath.toString());

			} else {
				throw new Exception("Could not read the file!");
			}
		}

		return file;
	}

	/**
	 * Saves a file to the local disk and to S3.
	 *
	 * @param inputFile          the multipart file to save
	 * @param fileName           the file name
	 * @param localDirectoryPath the local directory path
	 * @param awsDirectoryPath   the AWS directory path
	 * @param maxFileSize        the maximum size in bytes the file is allowed to
	 *                           be, or 0 or -1 if no size limit
	 * @param allowedFileTypes   a list of file type extensions allowed to be saved,
	 *                           or an empty list if no restrictions
	 * @param fileNameToDelete   the file name of a previous version of the file to
	 *                           be deleted from the local disk and S3, or null or
	 *                           empty if no delete is to be performed
	 * @return the file
	 * @throws Exception the exception
	 */
	public static File saveCachedFile(final MultipartFile inputFile, final String fileName,
			final String localDirectoryPath, final String awsDirectoryPath, final int maxFileSize,
			final List<String> allowedFileTypes, final String fileNameToDelete) throws Exception {

		// ensure file exists
		if (inputFile == null) {
			throw new RestException(false, 417, "Failed expectation", "Uploaded file is null");
		}

		// check for file
		if (inputFile.getOriginalFilename() == null) {
			throw new RestException(false, 417, "Failed expectation", "Uploaded file has null filename");
		}

		// check file size if required
		if (maxFileSize > 0 && inputFile.getSize() > maxFileSize) {
			throw new RestException(false, 413, "Failed expectation",
					"File size must be less than " + (maxFileSize / 1000000) + " MB");
		}

		final String originalFilename = inputFile.getOriginalFilename();
		final String extension = (StringUtils.hasText(originalFilename))
				? getFileExtension(originalFilename).toLowerCase()
				: "";

		// check file type if required
		if (allowedFileTypes.size() > 0 && !allowedFileTypes.contains("." + extension)) {
			throw new RestException(false, 417, "Failed expectation",
					"Format must be one of " + org.apache.commons.lang3.StringUtils.join(allowedFileTypes, " ") + ".");
		}

		final String localFilePath = Paths.get(localDirectoryPath + File.separator).toString();
		final String awsUploadPath = awsDirectoryPath;
		final File file = new File(localDirectoryPath + File.separator + fileName);

		LOG.debug("saveCachedFile localFilePath: " + file.getPath());

		// write to local directory
		try (final InputStream inputStream = inputFile.getInputStream()) {
			FileUtils.copyInputStreamToFile(inputStream, file);
		}

		// if required delete the previous version of the file
		if (fileNameToDelete != null && !fileNameToDelete.equals("")) {

			Files.deleteIfExists(Paths.get(localDirectoryPath + File.separator + fileNameToDelete));
			S3ConnectionWrapper.deleteObjectFromAws(awsUploadPath + fileNameToDelete);
		}

		S3ConnectionWrapper.uploadToS3(awsUploadPath, localFilePath, fileName);
		LOG.debug("saveCachedFile awsUploadPath: " + awsUploadPath + S3ConnectionWrapper.getSeparator() + fileName);
		// LOG.debug("saveCachedFile getS3DirectoryListing: " +
		// S3ConnectionWrapper.getDirectoryListing(awsUploadPath));

		return file;
	}
}
