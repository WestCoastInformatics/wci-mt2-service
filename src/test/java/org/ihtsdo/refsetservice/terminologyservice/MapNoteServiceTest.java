package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.ihtsdo.refsetservice.Application;
import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.model.MapNote;
import org.ihtsdo.refsetservice.model.MapNoteImportResult;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Service-level tests for {@link MapNoteService} file import.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false", "mapping.workflow.concept.branch.enabled=false"
})
public class MapNoteServiceTest {

    /**
     * Disable concept branch side effects.
     */
    @BeforeEach
    public void disableConceptBranchSideEffects() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
    }

    /**
     * Unknown usernames fail the import and create no notes or users.
     *
     * @throws Exception the exception
     */
    @Test
    public void importRejectsUnknownUsersByDefault() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final User importer = context.getAdminUser();
            final String historicalUser = "Gunnar Misvær-" + System.currentTimeMillis();
            final MockMultipartFile file = notesFile("conceptCode|User name|Date|Map note text\n"
                + MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE + "|" + historicalUser + "|2021-05-31 04:35:31|Historical note\n");

            final MapNoteImportResult result = MapNoteService.importNotes(service, importer, context.getMapSet(), file, false);

            assertFalse(result.isSuccess());
            assertTrue(result.getPreview().getUnknownUsers().contains(historicalUser));
            assertEquals(0, MapNoteService.getNotes(service, context.getMapSet(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE).size());
            assertNull(MapNoteService.findMapUser(service, historicalUser));
        }
    }

    /**
     * createMissingUsers creates attribution-only VIEWER map users and imports notes.
     *
     * @throws Exception the exception
     */
    @Test
    public void importCreatesMissingUsersWhenRequested() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final User importer = context.getAdminUser();
            final String historicalUser = "Gunnar Misvær-" + System.currentTimeMillis();
            final MockMultipartFile file = notesFile("conceptCode|User name|Date|Map note text\n"
                + MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE + "|" + historicalUser
                + "|2021-05-31T04:35:31.009Z|Historical note\n");

            final MapNoteImportResult result = MapNoteService.importNotes(service, importer, context.getMapSet(), file, true);

            assertTrue(result.isSuccess());
            assertEquals(1, result.getNotes().size());

            final MapUser createdUser = MapNoteService.findMapUser(service, historicalUser);
            assertNotNull(createdUser);
            assertEquals(historicalUser, createdUser.getName());
            assertEquals(MapUserRole.VIEWER, createdUser.getApplicationRole());

            final List<MapNote> notes = MapNoteService.getNotes(service, context.getMapSet(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            assertEquals(1, notes.size());
            assertEquals(context.getMapSet().getRefSetCode(), notes.get(0).getRefSetCode());
            assertEquals(ModelUtility.toJson("Historical note"), notes.get(0).getNote());
            assertEquals("Historical note", ModelUtility.toJson(notes.get(0).getNote(), String.class));
            assertEquals(historicalUser, notes.get(0).getUser().getUserName());
            assertNotNull(notes.get(0).getTimestamp());
            assertEquals(notes.get(0).getTimestamp(), notes.get(0).getModified());
            assertEquals(notes.get(0).getTimestamp(), notes.get(0).getCreated());

            for (final MapNote note : notes) {
                service.remove(note);
            }
            service.remove(createdUser);
        }
    }

    /**
     * Existing map users are reused without createMissingUsers.
     *
     * @throws Exception the exception
     */
    @Test
    public void importUsesExistingMapUser() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final User importer = context.getAdminUser();
            final String existingUser = context.getSpecialistUser().getUserName();
            final MockMultipartFile file = notesFile(
                MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE + "|" + existingUser + "|2021-06-02 12:55:25|Existing user note\n");

            final MapNoteImportResult result = MapNoteService.importNotes(service, importer, context.getMapSet(), file);

            assertTrue(result.isSuccess());
            assertEquals(1, result.getNotes().size());
            assertEquals(existingUser, result.getNotes().get(0).getUser().getUserName());

            for (final MapNote note : result.getNotes()) {
                service.remove(note);
            }
        }
    }

    /**
     * Imported note text is JSON-encoded so the notes modal {@code JSON.parse} can display bracketed
     * specialist/GP comments.
     *
     * @throws Exception the exception
     */
    @Test
    public void importEncodesNoteTextForUiJsonParse() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final User importer = context.getAdminUser();
            final String existingUser = context.getSpecialistUser().getUserName();
            final String plainText = "[Specialist] Flyttet fra Pulmonary medicine 11. feb";
            final MockMultipartFile file = notesFile(
                MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE + "|" + existingUser + "|2021-02-12T01:50:40.000Z|" + plainText + "\n");

            final MapNoteImportResult result = MapNoteService.importNotes(service, importer, context.getMapSet(), file);

            assertTrue(result.isSuccess());
            assertEquals(ModelUtility.toJson(plainText), result.getNotes().get(0).getNote());
            assertEquals(plainText, ModelUtility.toJson(result.getNotes().get(0).getNote(), String.class));

            for (final MapNote note : result.getNotes()) {
                service.remove(note);
            }
        }
    }

    /**
     * Notes are stored by {@code refSetCode}, so a later map set version with a new UUID still sees
     * notes imported against an earlier version.
     *
     * @throws Exception the exception
     */
    @Test
    public void importNotesVisibleOnLaterMapSetVersion() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final User importer = context.getAdminUser();
            final String existingUser = context.getSpecialistUser().getUserName();
            final MockMultipartFile file = notesFile(
                MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE + "|" + existingUser + "|2021-06-02 12:55:25|Shared across versions\n");

            final MapNoteImportResult result = MapNoteService.importNotes(service, importer, context.getMapSet(), file);
            assertTrue(result.isSuccess());

            final MapSet laterVersion = newPublishedVersion(context.getMapSet());
            service.add(laterVersion);

            final List<MapNote> notes = MapNoteService.getNotes(service, laterVersion, MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            assertEquals(1, notes.size());
            assertEquals(context.getMapSet().getRefSetCode(), notes.get(0).getRefSetCode());
            assertEquals(ModelUtility.toJson("Shared across versions"), notes.get(0).getNote());

            for (final MapNote note : result.getNotes()) {
                service.remove(note);
            }
            service.remove(laterVersion);
        }
    }

    private static MapSet newPublishedVersion(final MapSet original) {

        final MapSet laterVersion = new MapSet();
        laterVersion.setRefSetCode(original.getRefSetCode());
        laterVersion.setRefSetName(original.getRefSetName());
        laterVersion.setName("Published " + System.currentTimeMillis());
        laterVersion.setVersionStatus(VersionStatus.PUBLISHED);
        laterVersion.setWorkflowStatus(WorkflowStatus.PUBLISHED);
        laterVersion.setFromBranchPath(original.getFromBranchPath());
        laterVersion.setFromTerminology(original.getFromTerminology());
        laterVersion.setFromVersion(original.getFromVersion());
        laterVersion.setToBranchPath(original.getToBranchPath());
        laterVersion.setToTerminology(original.getToTerminology());
        laterVersion.setToVersion(original.getToVersion());
        laterVersion.setVersion("2024-01-01");
        laterVersion.setModuleId(original.getModuleId());
        laterVersion.setBaseContentVersion(original.getBaseContentVersion());
        laterVersion.setInternationalContentVersion(original.getInternationalContentVersion());
        laterVersion.setProject(original.getProject());
        laterVersion.setMapProject(original.getMapProject());
        laterVersion.setMapBranchId("pub-branch-" + System.currentTimeMillis());
        laterVersion.setEditBranchId("pub-edit-" + System.currentTimeMillis());
        return laterVersion;
    }

    private static MockMultipartFile notesFile(final String contents) {

        return new MockMultipartFile("notesFile", "notes.txt", "text/plain", contents.getBytes(StandardCharsets.UTF_8));
    }

}
