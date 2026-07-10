package org.ihtsdo.refsetservice.terminologyservice;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.helpers.WorkflowType;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;

/**
 * Shared fixtures for mapping workflow service and API tests.
 */
public final class MappingWorkflowTestFixtures {

    /** Specialist user name. */
    public static final String SPECIALIST_USER = "specialistUser";

    /** Other specialist user name. */
    public static final String OTHER_SPECIALIST_USER = "otherSpecialistUser";

    /** Lead user name. */
    public static final String LEAD_USER = "leadUser";

    /** Admin user name. */
    public static final String ADMIN_USER = "adminUser";

    /** Viewer user name. */
    public static final String VIEWER_USER = "viewerUser";

    /** Default source concept code. */
    public static final String SOURCE_CONCEPT_CODE = "123456789";

    private MappingWorkflowTestFixtures() {

        // n/a
    }

    /**
     * Mutable test context with persisted mapset, project, and workflow rows.
     */
    public static final class Context implements AutoCloseable {

        private final TerminologyService service;

        private MapSet mapSet;

        private MapProject mapProject;

        private MappingWorkflow workflow;

        private MapUser specialistMapUser;

        private MapUser otherSpecialistMapUser;

        private MapUser leadMapUser;

        private MapUser adminMapUser;

        private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

        private User specialistUser;

        private User otherSpecialistUser;

        private User leadUser;

        private User adminUser;

        private User viewerUser;

        private Context(final TerminologyService service) {

            this.service = service;
            specialistUser = new User(uniqueUser(SPECIALIST_USER), "Specialist User", "specialist@test", null, null, null);
            otherSpecialistUser = new User(uniqueUser(OTHER_SPECIALIST_USER), "Other Specialist", "other@test", null, null, null);
            leadUser = new User(uniqueUser(LEAD_USER), "Lead User", "lead@test", null, null, null);
            adminUser = new User(uniqueUser(ADMIN_USER), "Admin User", "admin@test", null, null, null);
            viewerUser = new User(uniqueUser(VIEWER_USER), "Viewer User", "viewer@test", null, null, null);
        }

        private String uniqueUser(final String baseName) {

            return baseName + "-" + instanceId;
        }

        /**
         * Create a fresh in-edit fixture.
         *
         * @return the context
         * @throws Exception the exception
         */
        public static Context createInEdit() throws Exception {

            final TerminologyService terminologyService = new TerminologyService();
            terminologyService.setModifiedBy("test");
            terminologyService.setModifiedFlag(true);

            final Context context = new Context(terminologyService);
            context.persistFixture(true);
            return context;
        }

        /**
         * Create a fixture with mapset not in edit.
         *
         * @return the context
         * @throws Exception the exception
         */
        public static Context createReadyForEdit() throws Exception {

            final TerminologyService terminologyService = new TerminologyService();
            terminologyService.setModifiedBy("test");
            terminologyService.setModifiedFlag(true);

            final Context context = new Context(terminologyService);
            context.persistFixture(false);
            return context;
        }

        public TerminologyService getService() {

            return service;
        }

        public MapSet getMapSet() {

            return mapSet;
        }

        public MapProject getMapProject() {

            return mapProject;
        }

        public MappingWorkflow getWorkflow() {

            return workflow;
        }

        public User getSpecialistUser() {

            return specialistUser;
        }

        public User getOtherSpecialistUser() {

            return otherSpecialistUser;
        }

        public User getLeadUser() {

            return leadUser;
        }

        public User getAdminUser() {

            return adminUser;
        }

        public User getViewerUser() {

            return viewerUser;
        }

        public MapUser getSpecialistMapUser() {

            return specialistMapUser;
        }

        public MapUser getOtherSpecialistMapUser() {

            return otherSpecialistMapUser;
        }

        public MapUser getLeadMapUser() {

            return leadMapUser;
        }

        public MappingWorkflow reloadWorkflow() throws Exception {

            workflow = service.get(workflow.getId(), MappingWorkflow.class);
            return workflow;
        }

        public MapSet reloadMapSet() throws Exception {

            mapSet = service.get(mapSet.getId(), MapSet.class);
            return mapSet;
        }

        public int historyCount() throws Exception {

            final ResultList<MappingWorkflowHistory> history =
                MappingWorkflowService.getWorkflowHistory(service, workflow, new SearchParameters());
            return history.getItems().size();
        }

        public MappingWorkflowHistory latestHistory() throws Exception {

            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setSort("modified");
            searchParameters.setSortAscending(false);
            searchParameters.setLimit(1);
            final ResultList<MappingWorkflowHistory> history =
                MappingWorkflowService.getWorkflowHistory(service, workflow, searchParameters);
            return history.getItems().isEmpty() ? null : history.getItems().get(0);
        }

        public void setWorkflowAssignedTo(final String userName) throws Exception {

            workflow.setWorkflowStatus(MapWorkflowStatus.EDITING_IN_PROGRESS);
            workflow.setAssignedUser(userName);
            workflow.setAssignedAt(new Date());
            workflow.setLeaseExpiresAt(new Date(System.currentTimeMillis() + 3600000L));
            service.update(workflow);
            reloadWorkflow();
        }

        public void setWorkflowStatus(final MapWorkflowStatus status) throws Exception {

            workflow.setWorkflowStatus(status);
            if (status != MapWorkflowStatus.EDITING_IN_PROGRESS) {
                workflow.setAssignedUser(null);
                workflow.setAssignedAt(null);
                workflow.setLeaseExpiresAt(null);
            }
            service.update(workflow);
            reloadWorkflow();
        }

        @Override
        public void close() throws Exception {

            if (workflow != null && workflow.getId() != null) {
                for (final MappingWorkflowHistory row : MappingWorkflowService.getWorkflowHistory(service, workflow, new SearchParameters()).getItems()) {
                    service.remove(row);
                }
                service.remove(workflow);
            }
            if (mapSet != null && mapSet.getId() != null) {
                final Project project = mapSet.getProject();
                service.remove(mapSet);
                if (mapProject != null && mapProject.getId() != null) {
                    service.remove(mapProject);
                }
                if (project != null) {
                    final Edition edition = project.getEdition();
                    service.remove(project);
                    if (edition != null) {
                        final Organization organization = edition.getOrganization();
                        service.remove(edition);
                        if (organization != null) {
                            service.remove(organization);
                        }
                    }
                }
            }
            removeIfPresent(specialistMapUser);
            removeIfPresent(otherSpecialistMapUser);
            removeIfPresent(leadMapUser);
            removeIfPresent(adminMapUser);
            service.close();
        }

        private void removeIfPresent(final MapUser mapUser) throws Exception {

            if (mapUser != null && mapUser.getId() != null) {
                service.remove(mapUser);
            }
        }

        private void persistFixture(final boolean mapsetInEdit) throws Exception {

            final Organization organization = new Organization();
            organization.setName("MW Org " + System.currentTimeMillis());
            organization.setCrowdId("mw-org-" + System.currentTimeMillis());
            organization.setAffiliate(false);
            organization.setCountryCode("US");
            service.add(organization);

            final Edition edition = new Edition();
            edition.setName("MW Edition");
            edition.setShortName("MWED");
            edition.setAbbreviation("MW");
            edition.setBranch("MAIN/MW");
            edition.setMaintainerType("TEST");
            edition.setOrganization(organization);
            service.add(edition);

            final Project project = new Project();
            project.setName("MW Project " + System.currentTimeMillis());
            project.setEdition(edition);
            service.add(project);

            specialistMapUser = persistMapUser(specialistUser.getUserName(), MapUserRole.SPECIALIST);
            otherSpecialistMapUser = persistMapUser(otherSpecialistUser.getUserName(), MapUserRole.SPECIALIST);
            leadMapUser = persistMapUser(leadUser.getUserName(), MapUserRole.LEAD);
            adminMapUser = persistMapUser(adminUser.getUserName(), MapUserRole.ADMINISTRATOR);
            persistMapUser(viewerUser.getUserName(), MapUserRole.VIEWER);

            mapProject = new MapProject();
            mapProject.setName("MW MapProject " + System.currentTimeMillis());
            mapProject.setDescription("Mapping workflow test project");
            mapProject.setPrivateProject(false);
            mapProject.setPrimaryContactEmail("mw@test.local");
            mapProject.setSourceTerminology("TEST");
            mapProject.setSourceTerminologyVersion("2025-01-01");
            mapProject.setDestinationTerminology("TARGET");
            mapProject.setDestinationTerminologyVersion("2025-01-01");
            mapProject.setEditingCycleBeginDate(new Date());
            mapProject.setLatestPublicationDate(new Date());
            mapProject.setRefSetId("mw-refset");
            mapProject.setModuleId("51000202101");
            mapProject.setRefSetName("MW Refset");
            mapProject.setRuleBased(false);
            mapProject.setTeamBased(false);
            mapProject.setPublished(false);
            mapProject.setUseTags(false);
            mapProject.setMapNotesPublic(false);
            mapProject.setReverseMapPattern(false);
            mapProject.setEdition(edition);
            mapProject.setWorkflowType(WorkflowType.SIMPLE_PATH);

            final Set<MapUser> specialists = new HashSet<>();
            specialists.add(specialistMapUser);
            specialists.add(otherSpecialistMapUser);
            mapProject.setMapSpecialists(specialists);

            final Set<MapUser> leads = new HashSet<>();
            leads.add(leadMapUser);
            leads.add(adminMapUser);
            mapProject.setMapLeads(leads);
            service.add(mapProject);

            mapSet = new MapSet();
            mapSet.setRefSetCode("mw-mapset-" + System.currentTimeMillis());
            mapSet.setRefSetName("MW MapSet");
            mapSet.setName("MW MapSet " + System.currentTimeMillis());
            mapSet.setVersionStatus(VersionStatus.IN_DEVELOPMENT);
            mapSet.setWorkflowStatus(mapsetInEdit ? WorkflowStatus.IN_EDIT : WorkflowStatus.READY_FOR_EDIT);
            mapSet.setFromBranchPath("MAIN/TEST/2025-01-01");
            mapSet.setFromTerminology("TEST");
            mapSet.setFromVersion("2025-01-01");
            mapSet.setToBranchPath("TARGET/20250101");
            mapSet.setToTerminology("TARGET");
            mapSet.setToVersion("20250101");
            mapSet.setVersion("2025-01-01");
            mapSet.setModuleId("51000202101");
            mapSet.setBaseContentVersion("2025-01-01 TEST");
            mapSet.setInternationalContentVersion("2025-01-01 SNOMED CT core");
            mapSet.setProject(project);
            mapSet.setMapProject(mapProject);
            service.add(mapSet);

            workflow = new MappingWorkflow();
            workflow.setSourceConceptCode(SOURCE_CONCEPT_CODE);
            workflow.setWorkflowStatus(MapWorkflowStatus.NEW);
            workflow.setSpecialistSlot(1);
            workflow.setMapSet(mapSet);
            workflow.setMapProject(mapProject);
            service.add(workflow);
        }

        private MapUser persistMapUser(final String userName, final MapUserRole role) throws Exception {

            final MapUser mapUser = new MapUser();
            mapUser.setUserName(userName);
            mapUser.setName(userName);
            mapUser.setEmail(userName + "@test.local");
            mapUser.setApplicationRole(role);
            return service.add(mapUser);
        }
    }
}
