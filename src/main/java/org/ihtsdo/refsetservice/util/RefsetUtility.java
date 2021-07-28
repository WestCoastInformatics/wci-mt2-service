
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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for interacting with files.
 */
public final class RefsetUtility {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(RefsetUtility.class);


    /**
     * Instantiates an empty {@link RefsetUtility}.
     */
    private RefsetUtility() {
        // n/a
    }

    public static List<Map<String, String>> getSortedRefsetVersionList(final String refsetId,
            final TerminologyService service) throws Exception {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            final List<Map<String, String>> versionList = new ArrayList<>();
            final PfsParameter pfs = new PfsParameter();
            pfs.setSort("versionDate");
            pfs.setAscending(false);

            // ResultList<Refset> test = service.find("id:
            // 78659156-b6d6-4935-bcdf-c4692bcee10d", pfs, Refset.class, null);
            // logger.debug("******** test: " + ModelUtility.toJson(test));

            final ResultList<Refset> results = service
                    .find("refsetId: " + QueryParserBase.escape(refsetId), pfs, Refset.class, null);

            for (Refset refset : results.getItems()) {

                final Map<String, String> version = new HashMap<>();
                version.put("status", refset.getVersionStatus());
                version.put("refsetInternalId", refset.getId());

                boolean inDevelopmentVersionFound = false;
                if (refset.getVersionStatus().toLowerCase().equals("in development")) {

                    if (inDevelopmentVersionFound) {
                        throw new Exception(
                                "May only have a single version at 'in development' at any given time, and we found 2nd for refsetId: "
                                        + refset.getRefsetId());
                    }

                    version.put("date",
                            DateUtility.formatDate(new Date(), DateUtility.DATE_FORMAT_REVERSE, null));

                    versionList.add(0, version);

                    inDevelopmentVersionFound = true;
                } else if ("beta, published".contains(refset.getVersionStatus().toLowerCase())) {

                    version.put("date", DateUtility.formatDate(refset.getVersionDate(),
                            DateUtility.DATE_FORMAT_REVERSE, null));

                    if (versionList.isEmpty()) {
                        versionList.add(version);
                    } else {

                        final Date dateToInsert = refset.getVersionDate();
                        int versionIndex = (inDevelopmentVersionFound) ? 1 : 0;

                        for (Map<String, String> currentVersion : versionList) {

                            final Date dateInspecting = sdf.parse(currentVersion.get("date"));

                            if (dateToInsert.before(dateInspecting)) {
                                break;
                            }

                            versionIndex++;
                        }

                        versionList.add(versionIndex, version);
                    }
                }
            }

            return versionList;
        }

}
