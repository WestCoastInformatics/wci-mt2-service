
package org.ihtsdo.refsetservice.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;

import org.hibernate.CacheMode;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.Search;
import org.ihtsdo.refsetservice.handler.SearchHandler;
import org.ihtsdo.refsetservice.model.HasId;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA implementation of the root services.
 */
// @Component
public class TerminologyService implements RootService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(TerminologyService.class);

    /** The last modified flag. */
    private boolean lastModifiedFlag = true;

    /** The last modified by. */
    private String lastModifiedBy = null;

    /** The reindex. */
    private static boolean reindex = true;

    /** The search handler. */
    private static Map<String, SearchHandler> searchHandlerMap = null;

    /** The factory. */
    private static EntityManagerFactory factory = null;

    /** The manager. */
    private EntityManager manager;

    /** The transaction per operation. */
    private boolean transactionPerOperation = true;

    /** The transaction entity. */
    private EntityTransaction transaction;

    /** The model package. */
    private static String modelPackage = "org.ihtsdo.refsetservice.model";

    /** The model package 2. */
    private static String modelPackage2 = "org.ihtsdo.refsetservice.model.data";

    /** Configuration properties. */
    Properties properties;

    /**
     * Instantiates an empty {@link TerminologyService}.
     *
     * @throws Exception the exception
     */
    public TerminologyService() throws Exception {

        if (properties == null) {
            properties = PropertyUtility.getProperties();
        }

        // created once or if the factory has closed
        if (factory == null || !factory.isOpen()) {

            logger.debug("Setting root service entity manager factory. ", properties);
            factory = Persistence.createEntityManagerFactory("refsetservice-ds",
                    PropertyUtility.getPrefixedProperties("spring.jpa.properties.", true));
        }

        final String key = "search.handler";
        searchHandlerMap = new HashMap<>();

        for (final String handlerName : PropertyUtility.getProperty(key).split(",")) {

            if (handlerName.isEmpty()) {
                continue;
            }

            // Add handlers to map
            final SearchHandler handlerService =
                    HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName,
                            SearchHandler.class);
            searchHandlerMap.put(handlerName, handlerService);
        }

        if (!searchHandlerMap.containsKey(ModelUtility.DEFAULT)) {
            throw new Exception(
                    "search.handler." + ModelUtility.DEFAULT + " expected and does not exist.");
        }

        logger.debug("  initialize search handler = " + searchHandlerMap.values().stream()
                .map(f -> f.getName()).collect(Collectors.toSet()));

        validateInit();

        // created on each instantiation
        manager = factory.createEntityManager();
        transaction = manager.getTransaction();

        // If we're using DDL "create" mode, clear the indexes
        if (!reindex && "create".equals(properties.getProperty("hibernate.hbm2ddl.auto"))) {
            logger.info("  clear indexes");
            clearLuceneIndexes();
            computeLuceneIndexes(null);
            reindex = false;
        }
    }

    /* see superclass */
    @Override
    public void validateInit() throws Exception {

        // Verify everything initialized properly
        if (searchHandlerMap == null) {
            throw new Exception("Unable to initialize root service");
        }
    }

    /* see superclass */
    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends HasModified> getType(final String type) throws Exception {
        try {
            return (Class<? extends HasModified>) Class
                    .forName(modelPackage + "." + StringUtility.capitalize(type));
        } catch (final Exception e) {
            return (Class<? extends HasModified>) Class
                    .forName(modelPackage2 + "." + StringUtility.capitalize(type));

        }
    }

    /**
     * Open factory.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void openFactory() throws Exception {

        // if factory has not been instantiated or has been closed, open it
        if (factory == null) {
            throw new Exception("Factory is null, serious problem.");
        }
        if (!factory.isOpen()) {
            logger.debug("Setting root service entity manager factory.");
            factory = Persistence.createEntityManagerFactory("TermServiceDS", properties);
        }
    }

    /**
     * Close factory.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void closeFactory() throws Exception {
        if (factory.isOpen()) {
            factory.close();
        }
    }

    /**
     * Returns the transaction per operation.
     *
     * @return the transaction per operation
     */
    /* see superclass */
    @Override
    public boolean getTransactionPerOperation() {
        return transactionPerOperation;
    }

    /**
     * Sets the transaction per operation.
     *
     * @param transactionPerOperation the transaction per operation
     */
    /* see superclass */
    @Override
    public void setTransactionPerOperation(final boolean transactionPerOperation) {
        this.transactionPerOperation = transactionPerOperation;
    }

    /**
     * Begin transaction.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void beginTransaction() throws Exception {

        if (getTransactionPerOperation()) {
            throw new IllegalStateException(
                    "Error attempting to begin a transaction when using transactions per operation mode.");
        } else if (transaction != null && transaction.isActive()) {
            throw new IllegalStateException("Error attempting to begin a transaction when there "
                    + "is already an active transaction");
        }

        transaction = manager.getTransaction();
        transaction.begin();
    }

    /**
     * Commit.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void commit() throws Exception {

        if (getTransactionPerOperation()) {
            throw new IllegalStateException(
                    "Error attempting to commit a transaction when using transactions per operation mode.");
        } else if (transaction != null && !transaction.isActive()) {
            throw new IllegalStateException("Error attempting to commit a transaction when there "
                    + "is no active transaction");
        } else if (transaction != null) {
            transaction.commit();
            manager.clear();
        }
    }

    /**
     * Rollback.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void rollback() throws Exception {

        if (getTransactionPerOperation()) {
            throw new IllegalStateException(
                    "Error attempting to rollback a transaction when using transactions per operation mode.");
        } else if (transaction != null && !transaction.isActive()) {
            logger.debug("n/a");
        } else if (transaction != null) {
            transaction.rollback();
            manager.clear();
        }
    }

    /* see superclass */
    @Override
    public void close() throws Exception {
        if (manager.isOpen()) {
            manager.close();
        }
    }

    /**
     * Clear.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void clear() throws Exception {
        if (manager.isOpen()) {
            manager.clear();
        }
    }

    /**
     * Returns the entity manager.
     *
     * @return the entity manager
     * @throws Exception the exception
     */
    @Override
    public EntityManager getEntityManager() throws Exception {
        return manager;
    }

    /**
     * Apply pfs to query.
     *
     * @param queryStr the query str
     * @param pfs the pfs
     * @return the javax.persistence. query
     * @throws Exception the exception
     */
    public javax.persistence.Query applyPfsToJPQLQuery(final String queryStr,
        final PfsParameter pfs) throws Exception {
        final StringBuilder localQueryStr = new StringBuilder();
        localQueryStr.append(queryStr);

        // Query restriction assumes a driving table called "a"
        if (pfs != null) {
            // add an order by clause to end of the query, assume driving table
            // called
            // "a"
            if (pfs.getSort() != null) {
                localQueryStr.append(" order by a.").append(pfs.getSort());
            }
        }

        final javax.persistence.Query query = manager.createQuery(localQueryStr.toString());
        if (pfs != null && pfs.getOffset() > -1 && pfs.getLimit() > -1) {
            query.setFirstResult(pfs.getOffset());
            query.setMaxResults(pfs.getLimit());
        }
        return query;
    }

    /**
     * Retrieves the sort field value from an object.
     *
     * @param o the object
     * @param sortField the period-separated X list of sequential getX methods,
     *            e.g. a.b.c
     * @return the value of the requested sort field
     * @throws Exception the exception
     */
    @SuppressWarnings("null")
    public Object getSortFieldValue(final Object o, final String sortField) throws Exception {
        // split the fields for method retrieval, e.g. a.b.c. =
        // o.getA().getB().getC()
        final String[] splitFields = sortField.split("\\.");

        int i = 0;
        Method finalMethod = null;
        Object finalObject = o;

        while (i < splitFields.length) {
            finalMethod = finalObject.getClass()
                    .getMethod("get" + StringUtility.capitalize(splitFields[i]), new Class<?>[] {});
            finalMethod.setAccessible(true);
            finalObject = finalMethod.invoke(finalObject, new Object[] {});
            i++;
        }

        // verify that final object is actually a string, enum, or date
        if (!finalMethod.getReturnType().equals(String.class)
                && !finalMethod.getReturnType().isEnum()
                && !finalMethod.getReturnType().equals(Long.class)
                && !finalMethod.getReturnType().equals(Date.class)) {
            throw new Exception("Requested sort field value is not string, enum, or date value "
                    + finalMethod.getReturnType().getName());
        }
        return finalObject;

    }

    /**
     * Retrieves the sort field value from an object.
     *
     * @param o the object
     * @param sortField the period-separated X list of sequential getX methods,
     *            e.g. a.b.c
     * @return the value of the requested sort field
     * @throws Exception the exception
     */
    @SuppressWarnings("null")
    // package visibility
    Class<?> getSortFieldType(final Object o, final String sortField) throws Exception {
        // split the fields for method retrieval, e.g. a.b.c. =
        // o.getA().getB().getC()
        final String[] splitFields = sortField.split("\\.");

        int i = 0;
        Method finalMethod = null;
        Object finalObject = o;

        while (i < splitFields.length) {
            finalMethod = finalObject.getClass()
                    .getMethod("get" + StringUtility.capitalize(splitFields[i]), new Class<?>[] {});
            finalMethod.setAccessible(true);
            finalObject = finalMethod.invoke(finalObject, new Object[] {});
            i++;
        }

        // verify that final object is actually a string, enum, or date
        if (!finalMethod.getReturnType().equals(String.class)
                && !finalMethod.getReturnType().isEnum()
                && !finalMethod.getReturnType().equals(Long.class)
                && !finalMethod.getReturnType().equals(Date.class)) {
            throw new Exception("Requested sort field value is not string, enum, or date value "
                    + finalMethod.getReturnType().getName());
        }
        return finalMethod.getReturnType();

    }

    /**
     * Apply pfs to List.
     *
     * @param <T> the
     * @param list the list
     * @param clazz the clazz
     * @param totalCt the total ct
     * @param pfs the pfs
     * @return the paged, filtered, and sorted list
     * @throws Exception the exception
     */
    @Override
    public <T> List<T> applyPfsToList(final List<T> list, final Class<T> clazz, final int[] totalCt,
        final PfsParameter pfs) throws Exception {

        // Skip empty pfs
        if (pfs == null) {
            return list;
        }

        // NOTE: does not handle active/inactive logic

        List<T> result = list;

        // check if sorting required

        List<String> pfsSortFields = new ArrayList<>();

        // if sort field specified, add to list of sort fields
        if (pfs.getSort() != null && !pfs.getSort().isEmpty()) {
            pfsSortFields.add(pfs.getSort());
        }

        // otherwise, if multiple sort fields specified
        else if (pfs.getSortFields() != null && !pfs.getSortFields().isEmpty()) {
            pfsSortFields = pfs.getSortFields();
        }

        // if one or more sort fields found, apply sorting
        if (!pfsSortFields.isEmpty() && !pfsSortFields.contains("RANDOM")) {

            // declare the final ascending flag and sort fields for comparator
            final boolean ascending = pfs.isAscending();
            final List<String> sortFields = pfsSortFields;

            // sort the list
            Collections.sort(result, new Comparator<T>() {

                @SuppressWarnings("null")
                @Override
                public int compare(final T t1, final T t2) {
                    // if an exception is returned, simply pass equality
                    try {

                        for (final String sortField : sortFields) {
                            final Object s1 = getSortFieldValue(t1, sortField);
                            final Object s2 = getSortFieldValue(t2, sortField);

                            final boolean isDate = s1 instanceof Date;
                            final boolean isLong = s1 instanceof Long;

                            // if both values null, skip to next sort field
                            if (s1 != null || s2 != null) {

                                // handle date comparison by long value
                                if (isDate || isLong) {
                                    final Long l1 = s1 == null ? null
                                            : (isDate ? ((Date) s1).getTime() : ((Long) s1));
                                    final Long l2 = s2 == null ? null
                                            : (isDate ? ((Date) s2).getTime() : ((Long) s2));

                                    if (ascending) {
                                        if (l1 == null && s2 != null) {
                                            return -1;
                                        }
                                        if (l1 != null && l1.compareTo(l2) != 0) {
                                            return l1.compareTo(l2);
                                        } else {
                                            return 0;
                                        }
                                    } else {
                                        if (l2 == null && l1 != null) {
                                            return -1;
                                        }
                                        if (l2 != null && l2.compareTo(l1) != 0) {
                                            return l2.compareTo(l1);
                                        } else {
                                            return 0;
                                        }
                                    }
                                }

                                // otherwise handle via string comparison
                                else if (ascending) {
                                    if (s1 == null && s2 != null) {
                                        return -1;
                                    }
                                    if (s1 != null && s1.toString().compareTo(s2.toString()) != 0) {
                                        return s1.toString().compareTo(s2.toString());
                                    } else {
                                        return 0;
                                    }
                                } else {
                                    if (s2 == null && s1 != null) {
                                        return -1;
                                    }
                                    if (s2 != null
                                            && (s2.toString()).compareTo(s1.toString()) != 0) {
                                        return (s2.toString()).compareTo(s1.toString());
                                    } else {
                                        return 0;
                                    }
                                }
                            }
                        }
                        // if no return after checking all sort fields, return
                        // equality
                        return 0;
                    } catch (final Exception e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
            });
        }

        // support RANDOM
        else if (pfsSortFields.contains("RANDOM")) {
            final Random random = new Random(new Date().getTime());
            Collections.sort(result, new Comparator<T>() {

                @Override
                public int compare(final T arg0, final T arg1) {
                    return random.nextInt();
                }
            });

        }

        // set the total count
        totalCt[0] = result.size();

        // get the start and end indexes based on paging parameters
        int startIndex = 0;

        int toIndex = result.size();
        if (pfs.getOffset() != -1) {
            startIndex = pfs.getOffset();
            // End of the list, or ...
            toIndex = (pfs.getLimit() == -1) ? result.size()
                    : Math.min(result.size(), startIndex + pfs.getLimit());
            if (startIndex > toIndex) {
                startIndex = 0;
            }
            result = result.subList(startIndex, toIndex);
        }

        return result;
    }

    /* see superclass */
    @Override
    public void setModifiedFlag(final boolean lastModifiedFlag) {
        this.lastModifiedFlag = lastModifiedFlag;
    }

    /* see superclass */
    @Override
    public boolean isModifiedFlag() {
        return lastModifiedFlag;
    }

    /**
     * Commit clear begin.
     *
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void commitClearBegin() throws Exception {
        commit();
        clear();
        beginTransaction();
    }

    /**
     * Log and commit.
     *
     * @param objectCt the object ct
     * @param logCt the log ct
     * @param commitCt the commit ct
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public void logAndCommit(final int objectCt, final int logCt, final int commitCt)
        throws Exception {
        // log at regular intervals
        if (objectCt % logCt == 0 && objectCt > 0) {
            logger.info("    count = " + objectCt);
        }
        if (objectCt % commitCt == 0) {
            commitClearBegin();
        }
    }

    /**
     * Adds the has last modified.
     *
     * @param <T> the
     * @param hasLastModified the has last modified
     * @return the t
     * @throws Exception the exception
     */
    public <T extends HasModified> T addHasLastModified(final T hasLastModified) throws Exception {

        // set last modified fields (user, timestamp)
        if (isModifiedFlag()) {
            if (getModifiedBy() == null) {
                throw new Exception("Service cannot add object, name of modifying user required");
            } else {
                hasLastModified.setModifiedBy(getModifiedBy());
            }
            hasLastModified.setModified(new Date());
            // Set timestamp if not set
            if (hasLastModified.getCreated() == null) {
                hasLastModified.setCreated(hasLastModified.getModified());
            }

        }

        // NO need to do this, instead handled via @PrePersist, and @PreUpdate
        // // Set the database "data" field, do this AFTER the part above so
        // those
        // // values are included
        // if (hasLastModified instanceof HasJsonData) {
        // ((HasJsonData) hasLastModified).marshall();
        // }

        return addObject(hasLastModified);
    }

    /* see superclass */
    @Override
    public <T extends Object> T addObject(final T object) throws Exception {
        try {
            // add
            if (getTransactionPerOperation()) {
                transaction = manager.getTransaction();
                transaction.begin();
                manager.persist(object);
                transaction.commit();
            } else {
                manager.persist(object);
            }
            return object;
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        }
    }

    /**
     * Update has last modified.
     *
     * @param <T> the
     * @param hasLastModified the has last modified
     * @throws Exception the exception
     */
    public <T extends HasModified> void updateHasLastModified(final T hasLastModified)
        throws Exception {

        // set last modified fields (user, timestamp)
        if (isModifiedFlag()) {
            if (getModifiedBy() == null) {
                throw new Exception(
                        "Service cannot update object, name of modifying user required");
            } else {
                hasLastModified.setModifiedBy(getModifiedBy());
            }
            hasLastModified.setModified(new Date());
        }

        // NO need to do this, instead handled via @PrePersist, and @PreUpdate
        // Set the database "data" field, do this AFTER the part above so those
        // values are included
        // if (hasLastModified instanceof HasJsonData) {
        // ((HasJsonData) hasLastModified).marshall();
        // }

        updateObject(hasLastModified);

    }

    /* see superclass */
    @Override
    public <T extends Object> void updateObject(final T object) throws Exception {
        try {
            // update
            if (getTransactionPerOperation()) {
                transaction = manager.getTransaction();
                transaction.begin();
                manager.merge(object);
                transaction.commit();
            } else {
                manager.merge(object);
            }
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        }

    }

    /**
     * Removes the has last modified.
     *
     * @param <T> the
     * @param id the id
     * @param clazz the clazz
     * @return the t
     * @throws Exception the exception
     */
    public <T extends HasModified> T removeHasLastModified(final String id, final Class<T> clazz)
        throws Exception {
        try {
            // Get transaction and object
            transaction = manager.getTransaction();
            final T hasLastModified = manager.find(clazz, id);

            // set last modified fields (user, timestamp)
            if (isModifiedFlag()) {
                if (getModifiedBy() == null) {
                    throw new Exception(
                            "Service cannot remove object, name of modifying user required");
                } else {
                    hasLastModified.setModifiedBy(getModifiedBy());
                }
                hasLastModified.setModified(new Date());
            }

            // Remove
            if (getTransactionPerOperation()) {
                // remove refset member
                transaction.begin();
                if (manager.contains(hasLastModified)) {
                    manager.remove(hasLastModified);
                } else {
                    manager.remove(manager.merge(hasLastModified));
                }
                transaction.commit();
            } else {
                if (manager.contains(hasLastModified)) {
                    manager.remove(hasLastModified);
                } else {
                    manager.remove(manager.merge(hasLastModified));
                }
            }
            return hasLastModified;
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        }

    }

    /* see superclass */
    @Override
    public <T extends Object> void removeObject(final T object) throws Exception {
        try {
            // Get transaction and object
            transaction = manager.getTransaction();
            // Remove
            if (getTransactionPerOperation()) {
                // remove refset member
                transaction.begin();
                if (manager.contains(object)) {
                    manager.remove(object);
                } else {
                    manager.remove(manager.merge(object));
                }
                transaction.commit();
            } else {
                if (manager.contains(object)) {
                    manager.remove(object);
                } else {
                    manager.remove(manager.merge(object));
                }
            }
        } catch (final Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
        }
    }

    /**
     * Returns the checks for object.
     *
     * @param <T> the
     * @param id the id
     * @param clazz the clazz
     * @return the checks for object
     * @throws Exception the exception
     */
    protected <T extends Object> T getObject(final String id, final Class<T> clazz)
        throws Exception {
        // Get transaction and object
        transaction = manager.getTransaction();
        final T component = manager.find(clazz, id);
        return component;
    }

    /* see superclass */
    @Override
    public void lockObject(final Object object) throws Exception {
        manager.lock(object, LockModeType.PESSIMISTIC_WRITE);
    }

    /* see superclass */
    @Override
    public void unlockObject(final Object object) {
        manager.lock(object, LockModeType.NONE);
    }

    /* see superclass */
    @Override
    public boolean isObjectLocked(final Object object) throws Exception {
        return manager.getLockMode(object).equals(LockModeType.PESSIMISTIC_WRITE);
    }

    /* see superclass */
    @Override
    public void refreshCaches() throws Exception {
        // init();
        closeFactory();
        openFactory();
    }

    /* see superclass */
    @Override
    public String getModifiedBy() {
        return lastModifiedBy;
    }

    /* see superclass */
    @Override
    public void setModifiedBy(final String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    /* see superclass */
    @Override
    public <T extends HasId> T get(final String id, final Class<T> clazz) throws Exception {
        if (id == null) {
            return null;
        }
        // Get transaction and object
        transaction = manager.getTransaction();
        final T component = manager.find(clazz, id);

        // NO need to do this because of @PostLoad umarshall handler
        // if (component instanceof HasJsonData) {
        // ((HasJsonData) component).unmarshall();
        // }
        return component;
    }

    /* see superclass */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends HasModified> List<T> getAll(final Class<T> clazz) throws Exception {
        try {
            final javax.persistence.Query query =
                    getEntityManager().createQuery("from " + clazz.getName());
            return query.getResultList();
        } catch (final NoResultException e) {
            return null;
        }
    }

    /* see superclass */
    @Override
    public <T extends HasId> ResultList<T> find(final String query, final PfsParameter pfs,
        final Class<T> clazz, final String handler) throws Exception {
        return find(new QueryParameter(query), pfs, clazz, handler);
    }

    /* see superclass */
    @Override
    public <T extends HasId> ResultList<String> findIds(final String query, final PfsParameter pfs,
        final Class<T> clazz, final String handler) throws Exception {
        return findIds(new QueryParameter(query), pfs, clazz, handler);
    }

    /* see superclass */
    @Override
    public <T extends HasId> int findTotal(final String query, final PfsParameter pfs,
        final Class<T> clazz, final String handler) throws Exception {
        return findTotal(new QueryParameter(query), pfs, clazz, handler);
    }

    /* see superclass */
    @Override
    public <T extends HasId> ResultList<T> find(final QueryParameter query, final PfsParameter pfs,
        final Class<T> clazz, final String handler) throws Exception {
        final ResultList<T> list = new ResultList<>();
        final int[] totalCt = new int[1];
        if (query == null) {
            list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler,
                    SearchHandler.class).getQueryResults("*:*", null, null, clazz, pfs, totalCt,
                            getEntityManager()));
        } else {
            list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler,
                    SearchHandler.class).getQueryResults(query.getQuery(),
                            query.getFieldedClauses(), query.getAdditionalClauses(), clazz, pfs,
                            totalCt, getEntityManager()));
        }
        list.setTotal(totalCt[0]);
        list.setLimit(pfs == null ? new PfsParameter().getLimit() : pfs.getLimit());
        list.setOffset(pfs == null ? new PfsParameter().getOffset() : pfs.getOffset());
        // NO need to do this because of @PostLoad umarshall handler
        // list.getItems().stream().peek(x -> {
        // try {
        // if (x instanceof HasJsonData) {
        // ((HasJsonData) x).unmarshall();
        // }
        // } catch (Exception e) {
        // logger.error("Unexpected error unmarshalling search result", e);
        // }
        // }).count();
        return list;
    }

    /* see superclass */
    @Override
    public <T extends HasId> ResultList<String> findIds(final QueryParameter query,
        final PfsParameter pfs, final Class<T> clazz, final String handler) throws Exception {
        final ResultList<String> list = new ResultList<>();
        final int[] totalCt = new int[1];
        if (query == null) {
            list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler,
                    SearchHandler.class).getIdResults("*:*", null, null, clazz, pfs, totalCt,
                            getEntityManager()));
        } else {
            list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler,
                    SearchHandler.class).getIdResults(query.getQuery(), query.getFieldedClauses(),
                            query.getAdditionalClauses(), clazz, pfs, totalCt, getEntityManager()));
        }
        list.setTotal(totalCt[0]);
        list.setLimit(pfs == null ? new PfsParameter().getLimit() : pfs.getLimit());
        list.setOffset(pfs == null ? new PfsParameter().getOffset() : pfs.getOffset());
        return list;
    }

    /* see superclass */
    @Override
    public <T extends HasId> int findTotal(final QueryParameter query, final PfsParameter pfs,
        final Class<T> clazz, final String handler) throws Exception {
        if (query == null) {
            return getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler,
                    SearchHandler.class).countQueryResults("*:*", null, null, clazz, pfs,
                            getEntityManager());
        }

        return getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler,
                SearchHandler.class).countQueryResults(query.getQuery(), query.getFieldedClauses(),
                        query.getAdditionalClauses(), clazz, pfs, getEntityManager());
    }

    /* see superclass */
    @Override
    public <T extends HasModified> T add(final T object) throws Exception {
        return addHasLastModified(object);
    }

    /* see superclass */
    @Override
    public <T extends HasModified> T update(final T object) throws Exception {
        updateHasLastModified(object);
        return object;
    }

    /* see superclass */
    @Override
    public <T extends HasModified> void remove(final T object) throws Exception {
        removeObject(object);
    }

    /* see superclass */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SearchHandler> List<T> getHandlers(final Class<T> type) throws Exception {
        if (type == SearchHandler.class) {
            final List<SearchHandler> list = new ArrayList<>();
            for (final SearchHandler handler : searchHandlerMap.values()) {
                list.add(handler);
            }
            return (List<T>) list;
        }
        return null;
    }

    /* see superclass */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SearchHandler> T getHandler(final Class<T> type) throws Exception {
        if (type == SearchHandler.class) {
            return (T) searchHandlerMap.get(ModelUtility.DEFAULT);
        }
        return null;
    }

    /* see superclass */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends SearchHandler> T getHandlerByName(final String name, final Class<T> type)
        throws Exception {
        if (type == SearchHandler.class) {
            if (!searchHandlerMap.containsKey(name)) {
                throw new Exception(
                        "Unexpected missing handler name for DefaultSearchHandler.class = " + name);
            }
            return (T) searchHandlerMap.get(name);
        }

        return null;
    }

    /**
     * New instance.
     *
     * @param <T> the
     * @param clazz the clazz
     * @return the t
     * @throws Exception the exception
     */
    @Override
    @SuppressWarnings({
            "unchecked", "deprecation"
    })
    public <T> T newInstance(final Class<T> clazz) throws Exception {
        final String simpleName = clazz.getSimpleName();
        final String jpaClassName =
                clazz.getName().replace(simpleName, "jpa." + simpleName + "Jpa");
        final Class<?> jpaClass = Class.forName(jpaClassName);
        if (jpaClass == null) {
            throw new Exception("Unable to find class " + jpaClassName);
        }
        return (T) jpaClass.newInstance();

    }

    /* see superclass */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends HasModified, S extends T> Class<S> getType(final Class<T> clazz)
        throws Exception {
        final String simpleName = clazz.getSimpleName();
        final String jpaClassName =
                clazz.getName().replace(simpleName, "jpa." + simpleName + "Jpa");
        return (Class<S>) Class.forName(jpaClassName);
    }

    /* see superclass */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends HasId> T copyInstance(final Class<T> clazz, final T object) throws Exception {
        final String simpleName = clazz.getSimpleName();
        final String jpaClassName =
                clazz.getName().replace(simpleName, "jpa." + simpleName + "Jpa");
        final Class<?> jpaClass = Class.forName(jpaClassName);
        if (jpaClass == null) {
            throw new Exception("Unable to find class " + jpaClassName);
        }
        final Constructor<?> jpaConstructor = jpaClass.getConstructor(clazz);
        return (T) jpaConstructor.newInstance(object);
    }

    /* see superclass */
    @Override
    public void computeLuceneIndexes(final String indexedObjects) throws Exception {
        // set of objects to be re-indexed
        final Set<String> objectsToReindex = new HashSet<>();
        final Map<String, Class<?>> reindexMap = new HashMap<>();
        final Reflections reflections =
                new Reflections(properties.getProperty("app.entity_packages"));
        for (final Class<?> clazz : reflections.getTypesAnnotatedWith(Indexed.class)) {
            reindexMap.put(clazz.getSimpleName(), clazz);
        }

        // if no parameter specified, re-index all objects
        if (indexedObjects == null || indexedObjects.isEmpty()) {

            // Add all class names
            for (final String className : reindexMap.keySet()) {
                if (objectsToReindex.contains(className)) {
                    // This restriction can be removed by using full class names
                    // however, then calling the mojo is more complicated
                    throw new Exception(
                            "Reindex process assumes simple class names are different.");
                }
                objectsToReindex.add(className);
            }

            // otherwise, construct set of indexed objects
        } else {

            // remove white-space and split by comma
            final String[] objects = indexedObjects.replaceAll(" ", "").split(",");

            // add each value to the set
            for (final String object : objects) {
                objectsToReindex.add(object);
            }

        }

        logger.info("starting reindexing for:");
        for (final String objectToReindex : objectsToReindex) {
            logger.info("  " + objectToReindex);
        }

        // Reindex each object
        for (final String key : reindexMap.keySet()) {
            // Concepts
            if (objectsToReindex.contains(key)) {
                logger.info("  creating indexes for " + key);
                final FullTextEntityManager fullTextEntityManager =
                        Search.getFullTextEntityManager(getEntityManager());

                try {
                    fullTextEntityManager.purgeAll(reindexMap.get(key));
                    fullTextEntityManager.flushToIndexes();
                    fullTextEntityManager.createIndexer(reindexMap.get(key))
                            .batchSizeToLoadObjects(100).cacheMode(CacheMode.IGNORE)
                            .idFetchSize(100).threadsToLoadObjects(10).startAndWait();
                } catch (final IllegalArgumentException e) {
                    logger.warn("      NOT AN ENTITY in this project");
                    // throw new Exception (e);
                }

                // if using elasticsearch the max result window size must be
                // increased
                if (properties
                        .getProperty("spring.jpa.properties.hibernate.search.default.indexmanager")
                        .trim().equals("elasticsearch")) {
                    IndexUtility.setMaxWindowSize(
                            reindexMap.get(key).toString().toLowerCase().replace("class ", ""));
                }

                // optimize flags are default true.
                objectsToReindex.remove(key);
            }
        }

        if (objectsToReindex.size() != 0) {
            throw new Exception("The following objects were specified for re-indexing, "
                    + "but do not exist as indexed objects: " + objectsToReindex.toString());
        }

    }

    /* see superclass */
    @Override
    public void clearLuceneIndexes() throws Exception {
        logger.info("  clearing lucene indexes");

        final Reflections reflections = new Reflections(properties.getProperty("index.packages"));
        final FullTextEntityManager fullTextEntityManager =
                Search.getFullTextEntityManager(getEntityManager());
        for (final Class<?> clazz : reflections.getTypesAnnotatedWith(Indexed.class)) {
            logger.info("    class = " + clazz.getName());
            try {
                fullTextEntityManager.purgeAll(clazz);
                fullTextEntityManager.flushToIndexes();
            } catch (final IllegalArgumentException e) {
                logger.warn("      NOT AN ENTITY in this project");
            }
        }
        // fullTextEntityManager.close();
    }

    @Override
    public <T extends HasModified> T findSingle(QueryParameter query, Class<T> clazz,
        String handler) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        pfs.setOffset(0);
        pfs.setLimit(2);
        if (query == null) {
            return null;
        }
        final List<T> list = getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler,
                SearchHandler.class).getQueryResults(query.getQuery(), query.getFieldedClauses(),
                        query.getAdditionalClauses(), clazz, pfs, new int[1], getEntityManager());
        if (list.size() == 0) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        throw new Exception("More than one object returned for the query - " + list.size() + ", "
                + list.toString());
    }

    @Override
    public <T extends HasModified> T findSingle(String query, Class<T> clazz, String handler)
        throws Exception {

        return findSingle(new QueryParameter(query), clazz, handler);
    }

    @Override
    public String getDbPrefix() throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String checkCache(String cache, String key) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addCache(String cache, String key, String value) throws Exception {
        // TODO Auto-generated method stub

    }

}
