
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
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for interacting with files.
 */
public final class FileUtility {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(FileUtility.class);

    /** Size of the buffer to read/write data. */
    private static final int BUFFER_SIZE = 4096;

    /**
     * Instantiates an empty {@link FileUtility}.
     */
    private FileUtility() {
        // n/a
    }

    /**
     * Extracts a zip file specified by the zipFilePath to a directory specified
     * by destDirectory (will be created if does not exists).
     *
     * @param zipFilePath the zip file path
     * @param destDirectory the dest directory
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void unzip(final String zipFilePath, final String destDirectory)
        throws IOException {
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
     * Zip.
     *
     * @param dirPath the dir path
     * @throws Exception the exception
     */
    public static void zip(final String dirPath) throws Exception {
        final Path sourceDir = Paths.get(dirPath);
        final String zipFileName = dirPath.concat(".zip");
        try (final ZipOutputStream outputStream =
                new ZipOutputStream(new FileOutputStream(zipFileName));) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file,
                    final BasicFileAttributes attributes) {
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
    private static void extractFile(final ZipInputStream zipIn, final String filePath)
        throws IOException {
        try (final BufferedOutputStream bos =
                new BufferedOutputStream(new FileOutputStream(filePath))) {
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
    @SuppressWarnings("resource")
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
            return IOUtils.toString(new BufferedInputStream(new URL(uri).openStream()), "UTF-8");
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
        if (filename.matches(".*\\.[A-Za-z0-9]+$")) {
            return filename.replaceAll(".*\\.([A-Za-z0-9]+)$", "$1");
        } else {
            return "";
        }
    }

    /**
     * Generate a list of line strings from a file removing empty lines.
     *
     * @param inputFile the input file
     * @return the line string List
     * @throws Exception the exception
     */
    public static List<String> readFileToArray(final String inputFile) throws Exception {

        List<String> lineArray = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {

            String line;

            while ((line = reader.readLine()) != null) {
                lineArray.add(line);
            }
        }

        return lineArray;
    }

}
