/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
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
     * Extracts a zip file specified by the zipFilePath to a directory specified by destDirectory (will be created if does not exists).
     *
     * @param zipFilePath the zip file path
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
     * @param in the in
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
     * Extract files from a zip archive.
     *
     * @param zipFilePath the path and filename of the zip file to unzip
     * @param extractionPath the path of the directory to extract files to
     * @return a list of file paths of the extracted files
     * @throws Exception the exception
     */
    public static List<String> unzipFiles(final String zipFilePath, final String extractionPath) throws Exception {

        final List<String> sourceFiles = new ArrayList<>();

        try (final ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));) {

            final File extractionDirectory = new File(extractionPath);
            final byte[] buffer = new byte[1024];
            ZipEntry zipEntry;

            while ((zipEntry = zis.getNextEntry()) != null) {

                final File newFile = new File(extractionDirectory, zipEntry.getName());
                final String extractionCanonicalPath = extractionDirectory.getCanonicalPath();
                final String fileCanonicalPath = newFile.getCanonicalPath();

                if (!fileCanonicalPath.startsWith(extractionCanonicalPath + File.separator)) {
                    throw new IOException("Entry is outside of the target directory: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }

                } else {

                    // fix for Windows-created archives
                    final File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    try (final FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, length);
                        }
                    }
                    sourceFiles.add(fileCanonicalPath);
                }
            }

            return sourceFiles;

        } catch (final Exception ex) {
            throw new Exception("Could not unzip the file: " + ex.getMessage(), ex);
        }
    }

    /**
     * Zip files.
     *
     * @param sourceFiles the source files
     * @param zipFileName the zip file name
     * @return the file
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static File zipFiles(final List<String> sourceFiles, final String zipFileName) throws IOException {

        final File zipFile = new File(zipFileName);
        try (final FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (final String filePath : sourceFiles) {
                final File fileToZip = new File(filePath);
                try (final FileInputStream fis = new FileInputStream(fileToZip)) {
                    final ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zos.putNextEntry(zipEntry);
                    final byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) >= 0) {
                        zos.write(buffer, 0, length);
                    }
                }
            }
        }
        return zipFile;
    }
    
    /**
     * Zip files together.
     *
     * @param sourceFiles the list of files to zip together
     * @param zipOutputFilePath the path and filename of the zip file to create
     * @throws Exception the exception
     */
    public static void zipFiles(final List<String> sourceFiles, final Path zipOutputFilePath) throws Exception {

        try (final FileOutputStream zipFileOutputStream = new FileOutputStream(zipOutputFilePath.toString());
            final ZipOutputStream zipOutputStream = new ZipOutputStream(zipFileOutputStream);) {

            for (final String sourceFile : sourceFiles) {
                final File fileToZip = new File(sourceFile);

                try (final FileInputStream zipFileInputStream = new FileInputStream(fileToZip)) {
                    final ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zipOutputStream.putNextEntry(zipEntry);

                    final byte[] bytes = new byte[1024];
                    int length;
                    while ((length = zipFileInputStream.read(bytes)) >= 0) {
                        zipOutputStream.write(bytes, 0, length);
                    }
                }
            }

        } catch (final Exception ex) {
            throw new Exception("Could not zip the files: " + ex.getMessage(), ex);
        }
    }

    /**
     * Zip files in a directory.
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
     * @param zipIn the zip in
     * @param filePath the file path
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void extractFile(final ZipInputStream zipIn, final String filePath) throws IOException {

        try (final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            final byte[] bytesIn = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }
    
    
    /**
     * Removes the effective time.
     *
     * @param origFilePath the orig file path
     * @throws Exception the exception
     */
    public static void removeEffectiveTime(final String origFilePath) throws Exception {

        LOG.info("Removing effectiveTime for non-PUBLISHED refsets.");

        try {
            final Path tempFilePath = Files.createTempFile("temp", ".txt");
            try (final BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)));
                final BufferedWriter writer = new BufferedWriter(new FileWriter(tempFilePath.toFile()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.isEmpty(line)) {

                        continue;
                    }

                    if (line.startsWith("id")) {
                        writer.write(line);
                        writer.newLine();
                        continue;
                    }

                    final String[] tokens = line.split("\t");
                    tokens[1] = "";
                    final String updatedLine = String.join("\t", tokens);
                    writer.write(updatedLine);
                    writer.newLine();
                }
            }
            // Replace the original file with the modified temporary file
            Files.move(tempFilePath, Path.of(origFilePath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("ERROR removing effectiveTime from file {}", origFilePath);
            throw e;
        }
    }
    
    /**
     * Removes the duplicate records.
     *
     * @param file the file
     * @return the list
     * @throws IOException Signals that an I/O exception has occurred.
     */
    // Method to remove duplicate records from a file
    public static List<String> removeDuplicateRecords(File file) throws IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines().distinct() // Filter out duplicate lines
                .collect(Collectors.toList());
        }
    }

    /**
     * Removes the Terminology folders/files .
     *
     * @param folderPath the folder path
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void processFolder(String folderPath) throws IOException {

        Path sourceFolder = Paths.get(folderPath);

        // Find the file to preserve based on the prefix "der2_iisssccRefset_"
        Optional<Path> targetFileOptional =
            Files.walk(sourceFolder).filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith("der2_iisssccRefset_")).findFirst();

        if (targetFileOptional.isEmpty()) {
            throw new IOException("No file starting with 'der2_iisssccRefset_' found in the folder: " + folderPath);
        }

        Path targetFile = targetFileOptional.get();

        // Move the mapSet file to a temporary location outside the source folder
        final Path builderDirectoryTempDir = Files.createTempDirectory("mt2MapSetPreserved-");
        Path tempTargetFile = builderDirectoryTempDir.resolve(targetFile.getFileName());
        Files.move(targetFile, tempTargetFile, StandardCopyOption.REPLACE_EXISTING);

        // Delete all other files and folders but not the folderPath
        // Delete children before parents
        Files.walk(sourceFolder).filter(path -> !path.equals(sourceFolder)).sorted((a, b) -> b.compareTo(a)).forEach(path -> {
            try {
                if (Files.isDirectory(path)) {
                    Files.deleteIfExists(path);
                } else {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error deleting file: " + path, e);
            }
        });

        // Move the preserved file from builderDirectoryTempDir folder to the root of
        // the source folder
        Path restoredFile = sourceFolder.resolve(tempTargetFile.getFileName());
        Files.move(tempTargetFile, restoredFile, StandardCopyOption.REPLACE_EXISTING);

        // Delete the temporary directory
        Files.delete(builderDirectoryTempDir);

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
            try (final InputStream in = FileUtility.class.getClassLoader().getResourceAsStream(uri.replaceFirst("classpath:", ""));) {
                if (in == null) {
                    throw new Exception("UNABLE to find classpath uri = " + fixuri);
                }
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            }
        } else {
            try (final InputStream in = new URL(uri).openStream()) {
                return IOUtils.toString(new BufferedInputStream(in), StandardCharsets.UTF_8);
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

        return (StringUtils.isNotBlank(fileExtension)) ? fileExtension : "";
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

        try (final BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {

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

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputFile.getInputStream()))) {

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
     * Returns an icon file from the local disk, loading it from S3 if it is not found locally.
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
     * @param inputFile the multipart file to save
     * @param fileNamePrefix the prefix of the file name before the timestamp
     * @param fileNameToDelete the file name of a previous version of the file to be deleted from the local disk and S3, or null or empty if no delete is to be
     *            performed
     * @return the file
     * @throws Exception the exception
     */
    public static File saveIconFile(final MultipartFile inputFile, final String fileNamePrefix, final String fileNameToDelete) throws Exception {

        if (inputFile == null) {
            throw new Exception("Icon file is null.  File name is " + fileNamePrefix);
        }

        final String originalFilename = inputFile.getOriginalFilename();

        if (StringUtils.isNotBlank(originalFilename)) {

            final String extension = getFileExtension(org.springframework.util.StringUtils.cleanPath(originalFilename)).toLowerCase();
            final String fileName = fileNamePrefix + "-" + (System.currentTimeMillis() / 1000L) + "." + extension;
            final int maxFileSize = Integer.valueOf(PropertyUtility.getProperty("refset.service.icon.file.maxsize"));
            final List<String> fileTypes = Arrays.asList(PropertyUtility.getProperty("refset.service.icon.file.types").split(";"));

            final File file =
                saveCachedFile(inputFile, fileName, serverIconDir, S3ConnectionWrapper.getAwsIconPath(), maxFileSize, fileTypes, fileNameToDelete);

            return file;
        }

        return null;
    }

    /**
     * Returns an artifact file from the local disk, loading it from S3 if it is not found locally.
     *
     * @param fileName the file name
     * @return the file
     * @throws Exception the exception
     */
    public static Resource getArtifactFile(final String fileName) throws Exception {

        return getCachedFile(fileName, serverArtifactDir, S3ConnectionWrapper.getAwsArtifactPath());

    }

    /**
     * Saves an artifact file to the local disk and to S3.
     *
     * @param inputFile the multipart file to save
     * @param fileNamePrefix the prefix of the file name before the timestamp
     * @param fileNameToDelete the file name of a previous version of the file to be deleted from the local disk and S3, or null or empty if no delete is to be
     *            performed
     * @return the file
     * @throws Exception the exception
     */
    public static File saveArtifactFile(final MultipartFile inputFile, final String fileNamePrefix, final String fileNameToDelete) throws Exception {

        if (inputFile == null) {
            throw new Exception("Artifact file is null.  File name is " + fileNamePrefix);
        }

        final String originalFilename = inputFile.getOriginalFilename();

        if (StringUtils.isNotBlank(originalFilename)) {
            final String extension = getFileExtension(originalFilename).toLowerCase();
            final String fileName = fileNamePrefix + "-" + (System.currentTimeMillis() / 1000L) + "." + extension;
            final int maxFileSize = -1;
            final List<String> fileTypes = new ArrayList<>();

            return 
                saveCachedFile(inputFile, fileName, serverArtifactDir, S3ConnectionWrapper.getAwsArtifactPath(), maxFileSize, fileTypes, fileNameToDelete);

        }
        return null;
    }

    /**
     * Returns a file from the local disk, loading it from S3 if it is not found locally.
     *
     * @param fileName the file name
     * @param localDirectoryPath the local directory path
     * @param awsDirectoryPath the AWS directory path
     * @return the file
     * @throws Exception the exception
     */
    public static Resource getCachedFile(final String fileName, final String localDirectoryPath, final String awsDirectoryPath) throws Exception {

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
     * @param inputFile the multipart file to save
     * @param fileName the file name
     * @param localDirectoryPath the local directory path
     * @param awsDirectoryPath the AWS directory path
     * @param maxFileSize the maximum size in bytes the file is allowed to be, or 0 or -1 if no size limit
     * @param allowedFileTypes a list of file type extensions allowed to be saved, or an empty list if no restrictions
     * @param fileNameToDelete the file name of a previous version of the file to be deleted from the local disk and S3, or null or empty if no delete is to be
     *            performed
     * @return the file
     * @throws Exception the exception
     */
    public static File saveCachedFile(final MultipartFile inputFile, final String fileName, final String localDirectoryPath, final String awsDirectoryPath,
        final int maxFileSize, final List<String> allowedFileTypes, final String fileNameToDelete) throws Exception {

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
            throw new RestException(false, 413, "Failed expectation", "File size must be less than " + (maxFileSize / 1000000) + " MB");
        }

        final String originalFilename = inputFile.getOriginalFilename();
        final String extension = (StringUtils.isNotBlank(originalFilename)) ? getFileExtension(originalFilename).toLowerCase() : "";

        // check file type if required
        if (!allowedFileTypes.isEmpty() && !allowedFileTypes.contains("." + extension)) {
            throw new RestException(false, 417, "Failed expectation",
                "Format must be one of " + org.apache.commons.lang3.StringUtils.join(allowedFileTypes, " ") + ".");
        }

        final String localFilePath = Paths.get(localDirectoryPath + File.separator).toString();
        final String awsUploadPath = awsDirectoryPath;
        final File file = new File(localDirectoryPath + File.separator + fileName);

        LOG.debug("saveCachedFile localFilePath: {}", file.getPath());

        // write to local directory
        try (final InputStream inputStream = inputFile.getInputStream()) {
            FileUtils.copyInputStreamToFile(inputStream, file);
        }

        // if required delete the previous version of the file
        if (StringUtils.isNotBlank(fileNameToDelete)) {

            Files.deleteIfExists(Paths.get(localDirectoryPath + File.separator + fileNameToDelete));
            S3ConnectionWrapper.deleteObjectFromAws(awsUploadPath + fileNameToDelete);
        }

        S3ConnectionWrapper.uploadToS3(awsUploadPath, localFilePath, fileName);
        LOG.debug("saveCachedFile awsUploadPath: {}{}{}", awsUploadPath, S3ConnectionWrapper.getSeparator(), fileName);

        return file;
    }
    
    
    /**
     * Creates a temporary directory for file operations.
     *
     * @param prefix the prefix for the directory name
     * @return the path to the temporary directory
     * @throws IOException if there is an error creating the directory
     */
    public static Path createTempDirectory(final String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    /**
     * Cleans up temporary files and directories.
     *
     * @param tempDir the temporary directory to clean up
     * @throws IOException if there is an error during cleanup
     */
    public static void cleanupTempFiles(final Path tempDir) throws IOException {
        if (tempDir != null) {
            try {
                FileUtility.deleteDirectory(tempDir.toFile());
            } catch (final Exception e) {
                throw new IOException("Failed to cleanup temporary files", e);
            }
        }
    }
}
