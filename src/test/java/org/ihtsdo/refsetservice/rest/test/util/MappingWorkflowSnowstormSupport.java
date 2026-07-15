package org.ihtsdo.refsetservice.rest.test.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.terminologyservice.BranchService;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowTestFixtures;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowTestFixtures.Context;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.PropertyUtility;

/**
 * Shared setup/assertions for mapping-workflow Snowstorm integration tests.
 */
public final class MappingWorkflowSnowstormSupport {

    /** Default edition branch used by local MT2/Snowstorm dev data. */
    public static final String DEFAULT_EDITION_BRANCH =
        "MAIN/SNOMEDCT-NO/2025-12-15";

    private static final String USERNAME_PROP =
        "terminology.handler.SNOMED_SNOWSTORM.username";

    private static final String PASSWORD_PROP =
        "terminology.handler.SNOMED_SNOWSTORM.password";

    private static final String REST_BASE_PROP =
        "terminology.handler.SNOMED_SNOWSTORM.restBaseUrl";

    private static Map<String, String> envLocalValues;

    private MappingWorkflowSnowstormSupport() {
        // n/a
    }

    /**
     * Load {@code .work/mt2-dev/.env-local} when Snowstorm env vars are unset.
     */
    public static void loadDevEnvLocalIfNeeded() {

        final boolean hasUser =
            StringUtils.isNotBlank(System.getenv("SNOMED_SNOWSTORM_USERNAME"));
        final boolean hasRest = StringUtils.isNotBlank(
            System.getenv("SNOMED_SNOWSTORM_REST_BASE_URL"));
        final boolean hasBase = StringUtils.isNotBlank(
            System.getenv("SNOMED_SNOWSTORM_BASE_URL"));
        if (hasUser && (hasRest || hasBase)) {
            return;
        }
        envLocalValue("SNOMED_SNOWSTORM_USERNAME");
    }

    /**
     * Value from {@code .work/mt2-dev/.env-local}, or null.
     *
     * @param key env key
     * @return value or null
     */
    public static String envLocalValue(final String key) {

        if (envLocalValues == null) {
            envLocalValues = readEnvLocal();
        }
        return envLocalValues.get(key);
    }

    private static Map<String, String> readEnvLocal() {

        final Path path = Paths.get(".work/mt2-dev/.env-local");
        if (!Files.isRegularFile(path)) {
            return Collections.emptyMap();
        }
        try {
            final List<String> lines =
                Files.readAllLines(path, StandardCharsets.UTF_8);
            final Map<String, String> values = new HashMap<>();
            for (final String raw : lines) {
                final String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String assignment = line;
                if (assignment.startsWith("export ")) {
                    assignment = assignment.substring("export ".length()).trim();
                }
                final int eq = assignment.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                final String name = assignment.substring(0, eq).trim();
                String value = assignment.substring(eq + 1).trim();
                if (isQuoted(value)) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(name, value);
            }
            return values;
        } catch (final IOException e) {
            return Collections.emptyMap();
        }
    }

    private static boolean isQuoted(final String value) {

        return (value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"));
    }

    /**
     * Fail hard unless Snowstorm credentials and the edition branch are available.
     *
     * @throws Exception if Snowstorm checks fail
     */
    public static void requireSnowstorm() throws Exception {

        final String username = PropertyUtility.getProperty(USERNAME_PROP);
        final String password = PropertyUtility.getProperty(PASSWORD_PROP);
        final String restBaseUrl = PropertyUtility.getProperty(REST_BASE_PROP);

        if (StringUtils.isBlank(username)
            || "test".equalsIgnoreCase(username.trim())) {
            fail("Snowstorm username is not configured. "
                + "Export SNOMED_SNOWSTORM_USERNAME "
                + "or ensure .work/mt2-dev/.env-local exists.");
        }
        if (StringUtils.isBlank(password)
            || "test".equalsIgnoreCase(password.trim())) {
            fail("Snowstorm password is not configured. "
                + "Export SNOMED_SNOWSTORM_PASSWORD "
                + "or ensure .work/mt2-dev/.env-local exists.");
        }
        if (StringUtils.isBlank(restBaseUrl)) {
            fail("Snowstorm restBaseUrl is not configured. "
                + "Export SNOMED_SNOWSTORM_REST_BASE_URL/BASE_URL "
                + "or ensure .work/mt2-dev/.env-local exists.");
        }

        SnowstormConnection.loadConfigurationFromProperties();

        final String editionBranch = editionBranch();
        if (!BranchService.doesBranchExist(editionBranch)) {
            fail("Snowstorm edition branch does not exist or is unreachable: "
                + editionBranch);
        }
    }

    /**
     * Edition branch under test.
     *
     * @return edition branch path
     */
    public static String editionBranch() {

        final String fromEnv = System.getenv("MT2_IT_EDITION_BRANCH");
        if (StringUtils.isNotBlank(fromEnv)) {
            return fromEnv.trim();
        }
        return DEFAULT_EDITION_BRANCH;
    }

    /**
     * Point fixture mapset at a real Snowstorm edition and create branches.
     *
     * @param context the workflow fixture
     * @param leadAlsoSpecialist when true, lead may ASSIGN/FINISH_EDITING
     * @throws Exception the exception
     */
    public static void wireSnowstormBranches(final Context context,
        final boolean leadAlsoSpecialist) throws Exception {

        final MapSet mapSet = context.getMapSet();
        final MapProject mapProject = context.getMapProject();
        final Edition edition = mapSet.getProject().getEdition();
        edition.setBranch(editionBranch());
        context.getService().update(edition);

        if (mapProject.getEdition() != null) {
            mapProject.getEdition().setBranch(editionBranch());
            context.getService().update(mapProject.getEdition());
        }

        if (leadAlsoSpecialist) {
            final Set<MapUser> specialists =
                new HashSet<>(mapProject.getMapSpecialists());
            specialists.add(context.getLeadMapUser());
            mapProject.setMapSpecialists(specialists);
            context.getService().update(mapProject);
        }

        final String mapBranchId = BranchService.generateBranchId();
        final String editBranchId = BranchService.generateBranchId();
        final int start = Math.max(0, mapBranchId.length() - 6);
        mapSet.setRefSetCode("9998" + mapBranchId.substring(start));
        mapSet.setMapBranchId(mapBranchId);
        mapSet.setEditBranchId(editBranchId);
        mapSet.setFromBranchPath(editionBranch());
        context.getService().update(mapSet);
        context.reloadMapSet();

        BranchService.createRefsetBranch(mapSet.toBranchDetails());
        BranchService.createEditBranch(mapSet.toBranchDetails(), editBranchId);
    }

    /**
     * Assert concept branch exists (or not) for the fixture mapset/concept.
     *
     * @param context fixture
     * @param conceptCode concept code
     * @param expectExists expected presence
     * @throws Exception the exception
     */
    public static void assertConceptBranch(final Context context,
        final String conceptCode, final boolean expectExists) throws Exception {

        final String path = BranchService.getConceptBranchPath(
            context.reloadMapSet(), conceptCode);
        final boolean exists = BranchService.doesBranchExist(path);
        if (expectExists) {
            assertTrue(exists, "Expected concept branch to exist: " + path);
        } else {
            assertFalse(exists, "Expected concept branch to be absent: " + path);
        }
    }

    /**
     * Best-effort cleanup of concept/edit/refset branches for the fixture.
     *
     * @param context fixture
     * @param conceptCode concept code
     */
    public static void cleanupBranches(final Context context,
        final String conceptCode) {

        try {
            final MapSet mapSet = context.reloadMapSet();
            final String conceptPath =
                BranchService.getConceptBranchPath(mapSet, conceptCode);
            if (BranchService.doesBranchExist(conceptPath)) {
                BranchService.deleteBranch(conceptPath);
            }
            final String editPath =
                BranchService.getEditBranchPath(mapSet.toBranchDetails());
            if (BranchService.doesBranchExist(editPath)) {
                BranchService.deleteBranch(editPath);
            }
            final String refsetPath =
                BranchService.getRefsetBranchPath(mapSet.toBranchDetails());
            if (BranchService.doesBranchExist(refsetPath)) {
                BranchService.deleteBranch(refsetPath);
            }
        } catch (final Exception e) {
            // Best effort; leftovers should not hide test failures.
        }
    }

}
