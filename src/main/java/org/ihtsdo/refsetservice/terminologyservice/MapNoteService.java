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
import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.model.MapNote;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for map notes stored in MT2 DB and keyed by map set + source concept code.
 */
public final class MapNoteService {

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
     * Deletes (soft-deactivates) a note.
     *
     * @param service the terminology service
     * @param user the authenticated user
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param noteId the note id
     * @throws Exception the exception
     */
    public static void deleteNote(final TerminologyService service, final User user, final MapSet mapSet, final String sourceConceptCode,
        final String noteId) throws Exception {

        final MapNote note = getNote(service, mapSet, sourceConceptCode, noteId);
        note.setActive(false);
        if (user != null && StringUtils.isNotBlank(user.getUserName())) {
            service.setModifiedBy(user.getUserName());
        }
        service.update(note);
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

        @SuppressWarnings("unchecked")
        final List<MapUser> existing = service.getEntityManager()
            .createQuery("from MapUser u where u.userName = :userName", MapUser.class)
            .setParameter("userName", user.getUserName())
            .setMaxResults(1)
            .getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        final MapUser mapUser = new MapUser();
        mapUser.setUserName(user.getUserName());
        mapUser.setName(StringUtils.defaultIfBlank(user.getName(), user.getUserName()));
        mapUser.setEmail(StringUtils.defaultIfBlank(user.getEmail(), user.getUserName() + "@unknown"));
        mapUser.setApplicationRole(MapUserRole.VIEWER);
        return service.add(mapUser);
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

}
