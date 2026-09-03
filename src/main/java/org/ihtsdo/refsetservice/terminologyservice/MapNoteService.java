/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.model.MapNote;
import org.ihtsdo.refsetservice.model.MapNoteImportPreview;
import org.ihtsdo.refsetservice.model.MapNoteImportResult;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for map notes stored in MT2 DB and keyed by map set {@code refSetCode} + source concept code.
 * APIs take a map set version; persistence uses the shared product code so notes survive publication.
 */
public final class MapNoteService {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(MapNoteService.class);

    /** Matches {@link MapNote#note} column length. */
    private static final int NOTE_COLUMN_LENGTH = 4000;

    /** Supported date formats for note import files. */
    private static final String[] IMPORT_DATE_FORMATS = {
        DateUtility.DATE_FORMAT_REVERSE_WITH_24_HOUR_TIME,
        DateUtility.DATE_FORMAT_REVERSE,
        DateUtility.DATE_FORMAT_REVERSE_ONLY_NUMBERS,
        DateUtility.DATE_FORMAT_US_STANDARD_WITH_24_HOUR_TIME,
        DateUtility.DATE_FORMAT_US_STANDARD,
        DateUtility.DATE_FORMAT_US_DASH_WITH_24_HOUR_TIME,
        DateUtility.DATE_FORMAT_US_DASH,
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ssXXX"
    };

    /**
     * Instantiates an empty {@link MapNoteService}.
     */
    private MapNoteService() {

        // n/a
    }

    /**
     * Returns notes for a map set + source concept, ordered by timestamp.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @return the notes
     * @throws Exception the exception
     */
    public static List<MapNote> getNotes(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode) throws Exception {

        validateKey(mapSet, sourceConceptCode);

        return service.getEntityManager()
            .createQuery("from MapNote n where n.refSetCode = :refSetCode and n.sourceConceptCode = :sourceConceptCode"
                + " and n.active = true order by n.timestamp asc", MapNote.class)
            .setParameter("refSetCode", mapSet.getRefSetCode())
            .setParameter("sourceConceptCode", sourceConceptCode)
            .getResultList();
    }

    /**
     * Returns a single note, verifying it belongs to the map set + concept.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param noteId the note id
     * @return the note
     * @throws Exception the exception
     */
    public static MapNote getNote(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode, final String noteId)
        throws Exception {

        validateKey(mapSet, sourceConceptCode);
        if (StringUtils.isBlank(noteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note id is required.");
        }

        final MapNote note = service.get(noteId, MapNote.class);
        if (note == null || !note.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Map note not found: " + noteId);
        }
        if (!mapSet.getRefSetCode().equals(note.getRefSetCode()) || !sourceConceptCode.equals(note.getSourceConceptCode())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Map note not found for map set/concept: " + noteId);
        }
        return note;
    }

    /**
     * Creates a note for a map set + source concept.
     *
     * @param service the terminology service
     * @param user the authenticated user
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param noteText the note text
     * @return the created note
     * @throws Exception the exception
     */
    public static MapNote createNote(final TerminologyService service, final User user, final MapSet mapSet, final String sourceConceptCode,
        final String noteText) throws Exception {

        validateKey(mapSet, sourceConceptCode);
        if (StringUtils.isBlank(noteText)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note text is required.");
        }
        if (user == null || StringUtils.isBlank(user.getUserName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required.");
        }

        final MapNote note = new MapNote();
        note.setRefSetCode(mapSet.getRefSetCode());
        note.setSourceConceptCode(sourceConceptCode);
        note.setNote(noteText.trim());
        note.setTimestamp(new Date());
        note.setUser(ensureMapUser(service, user));

        return service.add(note);
    }

    /**
     * Updates note text for an existing note.
     *
     * @param service the terminology service
     * @param user the authenticated user
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param noteId the note id
     * @param noteText the new note text
     * @return the updated note
     * @throws Exception the exception
     */
    public static MapNote updateNote(final TerminologyService service, final User user, final MapSet mapSet, final String sourceConceptCode,
        final String noteId, final String noteText) throws Exception {

        if (StringUtils.isBlank(noteText)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note text is required.");
        }

        final MapNote note = getNote(service, mapSet, sourceConceptCode, noteId);
        note.setNote(noteText.trim());
        if (user != null && StringUtils.isNotBlank(user.getUserName())) {
            service.setModifiedBy(user.getUserName());
        }
        return service.update(note);
    }

    /**
     * Deletes a note.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param noteId the note id
     * @throws Exception the exception
     */
    public static void deleteNote(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode, final String noteId)
        throws Exception {

        final MapNote note = getNote(service, mapSet, sourceConceptCode, noteId);
        service.remove(note);
    }

    /**
     * Imports map notes from a pipe-delimited file.
     * <p>
     * Expected format (optional header row):
     * {@code conceptCode|User name|Date|Map note text}
     * </p>
     * Validates the entire file first. If invalid, returns a failed result with preview details and
     * imports nothing. Unknown usernames are not auto-created unless {@code createMissingUsers} is true.
     * When created, they are attribution-only {@link MapUser} records (VIEWER), not login accounts.
     * Note text is stored as a JSON string literal so the notes modal {@code JSON.parse} can display it,
     * and {@code created}/{@code modified} are set to the file timestamp (the modal dates from {@code modified}).
     *
     * @param service the terminology service
     * @param importingUser the authenticated user performing the import
     * @param mapSet the map set
     * @param notesFile the import file
     * @return import result (notes on success, preview on failure)
     * @throws Exception the exception
     */
    public static MapNoteImportResult importNotes(final TerminologyService service, final User importingUser, final MapSet mapSet,
        final MultipartFile notesFile) throws Exception {

        return importNotes(service, importingUser, mapSet, notesFile, false);
    }

    /**
     * Imports map notes from a pipe-delimited file.
     *
     * @param service the terminology service
     * @param importingUser the authenticated user performing the import
     * @param mapSet the map set
     * @param notesFile the import file
     * @param createMissingUsers if true, create VIEWER {@link MapUser}s for unknown names
     * @return import result (notes on success, preview on failure)
     * @throws Exception the exception
     */
    public static MapNoteImportResult importNotes(final TerminologyService service, final User importingUser, final MapSet mapSet,
        final MultipartFile notesFile, final boolean createMissingUsers) throws Exception {

        if (importingUser == null || StringUtils.isBlank(importingUser.getUserName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required.");
        }

        final ValidatedNoteImport validated = validateNotesImport(service, mapSet, notesFile, createMissingUsers);
        if (!validated.preview.isValid()) {
            return MapNoteImportResult.failure(validated.preview);
        }

        final Map<String, MapUser> createdUsers = new HashMap<>();
        final List<MapNote> created = new ArrayList<>();
        for (final ParsedNoteRow row : validated.rows) {
            MapUser mapUser = row.mapUser;
            if (mapUser == null && createMissingUsers) {
                mapUser = createdUsers.get(row.userName);
                if (mapUser == null) {
                    mapUser = ensureMapUser(service, row.userName, row.userName, importedEmail(row.userName));
                    createdUsers.put(row.userName, mapUser);
                }
            }
            if (mapUser == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user: " + row.userName);
            }

            final MapNote note = new MapNote();
            note.setRefSetCode(mapSet.getRefSetCode());
            note.setSourceConceptCode(row.conceptCode);
            note.setUser(mapUser);
            note.setTimestamp(row.timestamp);
            note.setCreated(row.timestamp);
            note.setNote(row.noteText);
            final MapNote persisted = service.add(note);
            // The notes modal displays {@code modified}, not {@code timestamp}.
            persisted.setModified(row.timestamp);
            final boolean modifiedFlag = service.isModifiedFlag();
            try {
                service.setModifiedFlag(false);
                service.update(persisted);
            } finally {
                service.setModifiedFlag(modifiedFlag);
            }
            created.add(persisted);
        }

        LOG.info("Imported {} map notes for refSetCode={} (created {} missing map users)", created.size(), mapSet.getRefSetCode(), createdUsers.size());
        return MapNoteImportResult.success(created, validated.preview);
    }

    /**
     * Parses and validates a notes import file. Does not persist notes or create users.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param notesFile the import file
     * @param createMissingUsers if true, unknown usernames do not fail validation
     * @return validated rows and preview
     * @throws Exception the exception
     */
    private static ValidatedNoteImport validateNotesImport(final TerminologyService service, final MapSet mapSet, final MultipartFile notesFile,
        final boolean createMissingUsers) throws Exception {

        if (mapSet == null || StringUtils.isBlank(mapSet.getId()) || StringUtils.isBlank(mapSet.getRefSetCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Map set is required.");
        }
        if (notesFile == null || notesFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notes file is required.");
        }

        final List<String> lines = readNotesFileLines(notesFile);
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notes file is empty.");
        }

        final MapNoteImportPreview preview = new MapNoteImportPreview();
        final List<ParsedNoteRow> rows = new ArrayList<>();
        final Set<String> userNames = new LinkedHashSet<>();
        int lineNumber = 0;

        for (final String rawLine : lines) {
            lineNumber++;
            final String line = StringUtils.trimToEmpty(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (lineNumber == 1 && isHeaderLine(line)) {
                continue;
            }

            final String[] columns = line.split("\\|", 4);
            if (columns.length < 4) {
                preview.getErrors().add("Line " + lineNumber + ": expected conceptCode|User name|Date|Map note text");
                continue;
            }

            final String conceptCode = StringUtils.trimToEmpty(columns[0]);
            final String userName = StringUtils.trimToEmpty(columns[1]);
            final String dateText = StringUtils.trimToEmpty(columns[2]);
            final String noteText = StringUtils.trimToEmpty(columns[3]);

            if (StringUtils.isBlank(conceptCode) || StringUtils.isBlank(userName) || StringUtils.isBlank(dateText) || StringUtils.isBlank(noteText)) {
                preview.getErrors().add("Line " + lineNumber + ": conceptCode, user name, date, and note text are required");
                continue;
            }

            final String encodedNote;
            try {
                encodedNote = encodeNoteForUi(noteText);
            } catch (final Exception e) {
                preview.getErrors().add("Line " + lineNumber + ": note text could not be encoded");
                continue;
            }
            if (encodedNote.length() > NOTE_COLUMN_LENGTH) {
                preview.getErrors().add("Line " + lineNumber + ": note text exceeds " + NOTE_COLUMN_LENGTH + " characters after encoding");
                continue;
            }

            final Date timestamp;
            try {
                timestamp = parseImportDate(dateText);
            } catch (final Exception e) {
                preview.getErrors().add("Line " + lineNumber + ": invalid date '" + dateText + "'");
                continue;
            }

            final ParsedNoteRow row = new ParsedNoteRow();
            row.lineNumber = lineNumber;
            row.conceptCode = conceptCode;
            row.userName = userName;
            row.timestamp = timestamp;
            row.noteText = encodedNote;
            rows.add(row);
            userNames.add(userName);
        }

        preview.setTotalRows(rows.size());

        final Map<String, MapUser> mapUsersByUserName = new HashMap<>();
        for (final String userName : userNames) {
            final MapUser mapUser = findMapUser(service, userName);
            if (mapUser == null) {
                preview.getUnknownUsers().add(userName);
            } else {
                mapUsersByUserName.put(userName, mapUser);
            }
        }

        for (final ParsedNoteRow row : rows) {
            row.mapUser = mapUsersByUserName.get(row.userName);
        }

        preview.setValid(preview.getErrors().isEmpty() && preview.getTotalRows() > 0
            && (preview.getUnknownUsers().isEmpty() || createMissingUsers));
        if (preview.getTotalRows() == 0 && preview.getErrors().isEmpty()) {
            preview.getErrors().add("Notes file contains no data rows.");
            preview.setValid(false);
        }

        final ValidatedNoteImport validated = new ValidatedNoteImport();
        validated.preview = preview;
        validated.rows = rows;
        return validated;
    }

    /**
     * Finds an existing {@link MapUser} by user name. Does not create one.
     *
     * @param service the terminology service
     * @param userName the user name
     * @return the map user, or null if not found
     * @throws Exception the exception
     */
    public static MapUser findMapUser(final TerminologyService service, final String userName) throws Exception {

        if (StringUtils.isBlank(userName)) {
            return null;
        }

        final List<MapUser> existing = service.getEntityManager()
            .createQuery("from MapUser u where u.userName = :userName", MapUser.class)
            .setParameter("userName", userName)
            .setMaxResults(1)
            .getResultList();
        return existing.isEmpty() ? null : existing.get(0);
    }

    /**
     * Hydrates {@link Mapping#getMapNotes()} for a single mapping.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param mapping the mapping
     * @throws Exception the exception
     */
    public static void attachNotes(final TerminologyService service, final MapSet mapSet, final Mapping mapping) throws Exception {

        if (mapping == null || mapSet == null || StringUtils.isBlank(mapping.getCode())) {
            return;
        }
        mapping.setMapNotes(new LinkedHashSet<>(getNotes(service, mapSet, mapping.getCode())));
    }

    /**
     * Hydrates {@link Mapping#getMapNotes()} for a page of mappings in one query.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param mappings the mappings
     * @throws Exception the exception
     */
    public static void attachNotes(final TerminologyService service, final MapSet mapSet, final ResultListMapping mappings) throws Exception {

        if (mapSet == null || mappings == null || mappings.getItems() == null || mappings.getItems().isEmpty()) {
            return;
        }
        attachNotes(service, mapSet, mappings.getItems());
    }

    /**
     * Hydrates {@link Mapping#getMapNotes()} for a collection of mappings in one query.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param mappings the mappings
     * @throws Exception the exception
     */
    public static void attachNotes(final TerminologyService service, final MapSet mapSet, final Collection<Mapping> mappings) throws Exception {

        if (mapSet == null || mappings == null || mappings.isEmpty()) {
            return;
        }

        final Set<String> conceptCodes = mappings.stream().filter(m -> m != null && StringUtils.isNotBlank(m.getCode())).map(Mapping::getCode)
            .collect(Collectors.toCollection(HashSet::new));
        if (conceptCodes.isEmpty()) {
            return;
        }

        final List<MapNote> notes = service.getEntityManager()
            .createQuery("from MapNote n where n.refSetCode = :refSetCode and n.sourceConceptCode in :codes"
                + " and n.active = true order by n.timestamp asc", MapNote.class)
            .setParameter("refSetCode", mapSet.getRefSetCode())
            .setParameter("codes", conceptCodes)
            .getResultList();

        final Map<String, Set<MapNote>> notesByConcept = new HashMap<>();
        for (final MapNote note : notes) {
            notesByConcept.computeIfAbsent(note.getSourceConceptCode(), key -> new LinkedHashSet<>()).add(note);
        }

        for (final Mapping mapping : mappings) {
            if (mapping == null || StringUtils.isBlank(mapping.getCode())) {
                continue;
            }
            mapping.setMapNotes(new LinkedHashSet<>(notesByConcept.getOrDefault(mapping.getCode(), Collections.emptySet())));
        }
    }

    /**
     * Finds or creates a {@link MapUser} for the session user.
     *
     * @param service the terminology service
     * @param user the session user
     * @return the map user
     * @throws Exception the exception
     */
    private static MapUser ensureMapUser(final TerminologyService service, final User user) throws Exception {

        if (user == null || StringUtils.isBlank(user.getUserName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required.");
        }
        return ensureMapUser(service, user.getUserName(), user.getName(), user.getEmail());
    }

    /**
     * Finds or creates a {@link MapUser} for interactive note creation (session user only).
     *
     * @param service the terminology service
     * @param userName the user name
     * @param name the display name
     * @param email the email
     * @return the map user
     * @throws Exception the exception
     */
    private static MapUser ensureMapUser(final TerminologyService service, final String userName, final String name, final String email) throws Exception {

        if (StringUtils.isBlank(userName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User name is required.");
        }

        final MapUser existing = findMapUser(service, userName);
        if (existing != null) {
            return existing;
        }

        final MapUser mapUser = new MapUser();
        mapUser.setUserName(userName);
        mapUser.setName(StringUtils.defaultIfBlank(name, userName));
        mapUser.setEmail(StringUtils.defaultIfBlank(email, userName + "@unknown"));
        mapUser.setApplicationRole(MapUserRole.VIEWER);
        return service.add(mapUser);
    }

    /**
     * Parse a note import date. Accepts ISO-8601 instants (including {@code Z} and fractional
     * seconds) and the formats in {@link #IMPORT_DATE_FORMATS}.
     *
     * @param dateText the date text
     * @return the date
     * @throws Exception if the text cannot be parsed
     */
    private static Date parseImportDate(final String dateText) throws Exception {

        try {
            return Date.from(Instant.parse(dateText));
        } catch (final Exception e) {
            // fall through
        }
        try {
            return Date.from(OffsetDateTime.parse(dateText).toInstant());
        } catch (final Exception e) {
            // fall through
        }
        return DateUtils.parseDate(dateText, IMPORT_DATE_FORMATS);
    }

    /**
     * Encode note text the same way the Angular notes modal saves it ({@code JSON.stringify(text)}),
     * so {@code JSON.parse(item.note)} in the UI succeeds.
     *
     * @param noteText the plain note text from the import file
     * @return a JSON string literal, e.g. {@code "hello"}
     * @throws Exception if encoding fails
     */
    private static String encodeNoteForUi(final String noteText) throws Exception {

        return ModelUtility.toJson(noteText);
    }

    /**
     * Read notes file as UTF-8 so names such as {@code Gunnar Misvær} round-trip on Windows.
     *
     * @param notesFile the notes file
     * @return lines
     * @throws Exception the exception
     */
    private static List<String> readNotesFileLines(final MultipartFile notesFile) throws Exception {

        final List<String> lines = new ArrayList<>();
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(notesFile.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Placeholder email for attribution-only map users created during import.
     *
     * @param userName the user name
     * @return an email
     */
    private static String importedEmail(final String userName) {

        final String localPart = userName.trim().replaceAll("\\s+", ".");
        final String email = localPart + "@imported.invalid";
        return email.length() <= 255 ? email : email.substring(0, 255);
    }

    /**
     * Returns true if the line looks like an import header.
     *
     * @param line the line
     * @return true if header
     */
    private static boolean isHeaderLine(final String line) {

        final String normalized = line.toLowerCase();
        return normalized.startsWith("conceptcode|") || normalized.startsWith("concept code|");
    }

    /**
     * Validates map set + concept key.
     *
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     */
    private static void validateKey(final MapSet mapSet, final String sourceConceptCode) {

        if (mapSet == null || StringUtils.isBlank(mapSet.getId()) || StringUtils.isBlank(mapSet.getRefSetCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Map set is required.");
        }
        if (StringUtils.isBlank(sourceConceptCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source concept code is required.");
        }
    }

    /**
     * Parsed / validated note import file contents.
     */
    private static final class ValidatedNoteImport {

        /** Preview summary. */
        private MapNoteImportPreview preview;

        /** Parsed rows (may include rows with unknown users). */
        private List<ParsedNoteRow> rows;
    }

    /**
     * One parsed note import row.
     */
    private static final class ParsedNoteRow {

        /** Source line number. */
        private int lineNumber;

        /** Concept code. */
        private String conceptCode;

        /** User name from file. */
        private String userName;

        /** Note timestamp. */
        private Date timestamp;

        /** Note text. */
        private String noteText;

        /** Resolved map user, or null if unknown. */
        private MapUser mapUser;
    }

}
