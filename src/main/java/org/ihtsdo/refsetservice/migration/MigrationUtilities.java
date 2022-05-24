package org.ihtsdo.refsetservice.migration;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;

public class MigrationUtilities {

    static final String MODULE_ANCESTOR_CONCEPT_SCTID = "900000000000443000";

    private static final MigrationPropertyFileReader propertyReader = new MigrationPropertyFileReader();

    Project addProject(Organization org, String projectName, String projectDescription, MigrationMetadata meta) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setOrganization(org);
            project.setPrivateProject(false);
            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(projectName));

            // Persist
            setMetadata(project, meta);
            return service.add(project);
        }

    }

    Organization addOrganziation(final String orgName, String orgDesc, final Edition edition, final MigrationMetadata meta) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Organization org = new Organization();
            org.setName(orgName);
            org.setDescription(orgDesc);
            org.setEdition(edition);

            setMetadata(org, meta);

            return service.add(org);
        }

    }

    public Refset addRefset(String name, String refsetId, String moduleId, Date versionDate) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Refset refset = new Refset();

            refset.setName(name);
            refset.setRefsetId(refsetId);
            refset.setModuleId(moduleId);
            refset.setVersionStatus("PUBLISHED");
            refset.setWorkflowStatus("PUBLISHED");
            refset.setActive(true);
            refset.setVersionDate(versionDate);

            return service.add(refset);
        }

    }

    Set<DefinitionClause> addClause(String rttId) throws Exception {

        Set<DefinitionClause> refsetClauses = new HashSet<>();
        MigrationPropertyFileReader propertyReader = new MigrationPropertyFileReader();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            for (String clauseJson : propertyReader.getRttRefsetToClausesMap().get(rttId)) {

                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                setMetadata(clause, propertyReader.getMetadataMap().get("refset-" + rttId));
                DefinitionClause persistedClause = service.add(clause);
                refsetClauses.add(persistedClause);
            }

            return refsetClauses;
        }

    }

    void setMetadata(final HasModified object, final MigrationMetadata metadata) {

        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }

    private void initializeService(TerminologyService service) {

        service.setModifiedBy("Migration");
        service.setModifiedFlag(true);
    }

    MigrationPropertyFileReader getPropertyReader() {

        return propertyReader;
    }

}
