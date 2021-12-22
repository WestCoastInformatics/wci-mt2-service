package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalIdGetterUnitTestUtilities {
    private static Logger logger = LoggerFactory.getLogger(InternalIdGetterUnitTestUtilities.class);

    protected static SimpleDateFormat sdf = null;

    public InternalIdGetterUnitTestUtilities(SimpleDateFormat sdf) {
        this.sdf = sdf;
    }
    
    /**
     * Get the internal refset ID based on the refset's terminology specific ID
     * .
     * @param version
     * @param refsetWithInactiveConcept
     *
     * @return the internal refset ID
     * @throws Exception the exception
     */
    
    public String getRefsetInternalId(String refsetId, String version) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setSort("versionDate");
            pfs.setAscending(false);

            ResultList<Refset> refsets =
                    service.find("refsetId:" + QueryParserBase.escape(refsetId) + "", pfs,
                            Refset.class, null);

            assertThat(refsets.getItems().size()).isGreaterThan(0);

            Refset refsetToReturn = null;
            for (Refset refset : refsets.getItems()) {
                if (version.equals(sdf.format(refset.getVersionDate()))) {
                    refsetToReturn = refset;
                    break;
                }
            }

            if (refsetToReturn == null) {
                throw new Exception("Refset Id: " + refsetId
                        + " does not exist in the RT2 database");
            }

            assertThat(refsetToReturn).isNotNull();
            assertThat(refsetToReturn.getRefsetId()).isEqualTo(refsetId);

            return refsetToReturn.getId();
        }
    }

    /**
     * Get the internal project ID based on the project's name
     * 
     * @param projectName The name of the project
     * @return the internal project ID
     * @throws Exception the exception
     */
    public String getProjectInternalId(String projectName) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();

            ResultList<Project> projects = service.find("name:" + QueryParserBase.escape(projectName) + "",
                    pfs, Project.class, null);

            if (projects.getItems().size() == 0) {
                throw new Exception(
                        "Refset Internal Id: " + projectName + " does not exist in the RT2 database");
            }

            Project project = projects.getItems().get(0);

            assertThat(project.getName()).isEqualTo(projectName);

            return project.getId();
        }
    }

    /**
     * Get the internal edition ID based on the edition's name
     * 
     * @param name The name of the edition
     * @return the internal edition ID
     * @throws Exception the exception
     */
    public String getEditionInternalId(String name) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();

            ResultList<Edition> editions = service.find("name:" + QueryParserBase.escape(name) + "",
                    pfs, Edition.class, null);

            if (editions.getItems().size() == 0) {
                throw new Exception(
                        "Refset Internal Id: " + name + " does not exist in the RT2 database");
            }

            Edition edition = editions.getItems().get(0);

            assertThat(edition.getName()).isEqualTo(name);

            return edition.getId();
        }
    }


}
