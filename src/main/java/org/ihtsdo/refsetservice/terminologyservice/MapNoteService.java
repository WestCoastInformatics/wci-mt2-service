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
import org.ihtsdo.refsetservice.util.FileUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for map notes stored in MT2 DB and keyed by map set + source concept code.
 */
public final class MapNoteService {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(MapNoteService.class);

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
            .createQuery("from MapNote n where n.mapSet.id = :mapSetId and n.sourceConceptCode = :sourceConceptCode"
                + " and n.active = true order by n.timestamp asc", MapNote.class)
            .setParameter("mapSetId", mapSet.getId())
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
        if (note.getMapSet() == null || !mapSet.getId().equals(note.getMapSet().getId())
            || !sourceConceptCode.equals(note.getSourceConceptCode())) {
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
        note.setMapSet(mapSet);
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
     * imports nothing. Unknown usernames are never auto-created.
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

        if (importingUser == null || StringUtils.isBlank(importingUser.getUserName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required.");
        }

        final ValidatedNoteImport validated = validateNotesImport(service, mapSet, notesFile);
        if (!validated.preview.isValid()) {
            return MapNoteImportResult.failure(validated.preview);
        }

        final List<MapNote> created = new ArrayList<>();
        for (final ParsedNoteRow row : validated.rows) {
            final MapNote note = new MapNote();
            note.setMapSet(mapSet);
            note.setSourceConceptCode(row.conceptCode);
            note.setUser(row.mapUser);
            note.setTimestamp(row.timestamp);
            note.setNote(row.noteText);
            created.add(service.add(note));
        }

        LOG.info("Imported {} map notes for mapSet={}", created.size(), mapSet.getId());
        return MapNoteImportResult.success(created, validated.preview);
    }

    /**
     * Parses and validates a notes import file. Does not persist notes or create users.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param notesFile the import file
     * @return validated rows and preview
     * @throws Exception the exception
     */
    private static ValidatedNoteImport validateNotesImport(final TerminologyService service, final MapSet mapSet, final MultipartFile notesFile)
        throws Exception {

        if (mapSet == null || StringUtils.isBlank(mapSet.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Map set is required.");
        }
        if (notesFile == null || notesFile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notes file is required.");
        }

        final List<String> lines = FileUtility.readFileToArray(notesFile);
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

            final Date timestamp;
            try {
                timestamp = DateUtils.parseDate(dateText, IMPORT_DATE_FORMATS);
            } catch (final Exception e) {
                preview.getErrors().add("Line " + lineNumber + ": invalid date '" + dateText + "'");
                continue;
            }

            final ParsedNoteRow row = new ParsedNoteRow();
            row.lineNumber = lineNumber;
            row.conceptCode = conceptCode;
            row.userName = userName;
            row.timestamp = timestamp;
            row.noteText = noteText;
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

        preview.setValid(preview.getErrors().isEmpty() && preview.getUnknownUsers().isEmpty() && preview.getTotalRows() > 0);
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
            .createQuery("from MapNote n where n.mapSet.id = :mapSetId and n.sourceConceptCode in :codes"
                + " and n.active = true order by n.timestamp asc", MapNote.class)
            .setParameter("mapSetId", mapSet.getId())
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
    public static MapUser ensureMapUser(final TerminologyService service, final User user) throws Exception {

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

        if (mapSet == null || StringUtils.isBlank(mapSet.getId())) {
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
