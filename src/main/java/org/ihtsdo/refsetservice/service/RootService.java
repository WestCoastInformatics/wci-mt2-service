
package org.ihtsdo.refsetservice.service;

import java.util.List;

import javax.persistence.EntityManager;

import org.ihtsdo.refsetservice.handler.SearchHandler;
import org.ihtsdo.refsetservice.model.HasId;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.util.ResultList;

/**
 * Top level root services.
 */
public interface RootService extends AutoCloseable {

    /** The logging object ct threshold. */
    public static final int LOG_CT = 5000;

    /** The commit count. */
    public static final int COMMIT_CT = 2000;

    // This is a placeholder that normally accommodates logging, transactions,
    // factories, db connection, etc.

    /**
     * Refresh caches.
     *
     * @throws Exception the exception
     */
    public void refreshCaches() throws Exception;

    /**
     * Validate init.
     *
     * @throws Exception the exception
     */
    public void validateInit() throws Exception;

    /**
     * Apply pfs to list.
     *
     * @param <T> the
     * @param list the list
     * @param clazz the clazz
     * @param totalCt the total ct
     * @param pfs the pfs
     * @return the list
     * @throws Exception the exception
     */
    public <T> List<T> applyPfsToList(final List<T> list, final Class<T> clazz, final int[] totalCt, final PfsParameter pfs) throws Exception;

    /**
     * Checks if is modified flag.
     *
     * @return true, if is modified flag
     */
    public boolean isModifiedFlag();

    /**
     * Sets the modified flag.
     *
     * @param modifiedFlag the modified flag
     */
    public void setModifiedFlag(boolean modifiedFlag);

    /**
     * Returns the modified by.
     *
     * @return the modified by
     */
    public String getModifiedBy();

    /**
     * Sets the modified by.
     *
     * @param modifiedBy the modified by
     */
    public void setModifiedBy(String modifiedBy);

    /**
     * Lock Hibernate object.
     *
     * @param object the object
     * @throws Exception the exception
     */
    public void lockObject(Object object) throws Exception;

    /**
     * Unlock Hibernate object.
     *
     * @param object the object
     */
    public void unlockObject(Object object);

    /**
     * Is object locked.
     *
     * @param object the object
     * @return true, if is object locked
     * @throws Exception the exception
     */
    public boolean isObjectLocked(Object object) throws Exception;

    /**
     * Returns the type.
     *
     * @param type the type
     * @return the type
     * @throws Exception the exception
     */
    public Class<? extends HasModified> getType(String type) throws Exception;

    /**
     * Open factory.
     *
     * @throws Exception the exception
     */
    public void openFactory() throws Exception;

    /**
     * Close factory.
     *
     * @throws Exception the exception
     */
    public void closeFactory() throws Exception;

    /**
     * New instance of the object type - this binds a service implementation to an object impl.
     *
     * @param <T> the
     * @param clazz the clazz
     * @return the t
     * @throws Exception the exception
     */
    public <T> T newInstance(Class<T> clazz) throws Exception;

    /**
     * Returns the type.
     *
     * @param <T> the type
     * @param <S> the sub type
     * @param clazz the clazz
     * @return the type
     * @throws Exception the exception
     */
    public <T extends HasModified, S extends T> Class<S> getType(Class<T> clazz) throws Exception;

    /**
     * Copy instance.
     *
     * @param <T> the
     * @param clazz the clazz
     * @param object the object
     * @return the t
     * @throws Exception the exception
     */
    public <T extends HasId> T copyInstance(Class<T> clazz, T object) throws Exception;

    /**
     * Returns the model object.
     *
     * @param <T> the
     * @param id the id
     * @param clazz the clazz
     * @return the model object
     * @throws Exception the exception
     */
    public <T extends HasId> T get(String id, Class<T> clazz) throws Exception;

    /**
     * Returns the model objects.
     *
     * @param <T> the
     * @param clazz the clazz
     * @return the model objects
     * @throws Exception the exception
     */
    public <T extends HasModified> List<T> getAll(Class<T> clazz) throws Exception;

    /**
     * Find model objects.
     *
     * @param <T> the
     * @param query the query
     * @param pfs the pfs
     * @param clazz the clazz
     * @param handler the handler
     * @return the list
     * @throws Exception the exception
     */
    public <T extends HasId> ResultList<T> find(QueryParameter query, PfsParameter pfs, Class<T> clazz, String handler) throws Exception;

    /**
     * Find ids.
     *
     * @param <T> the
     * @param query the query
     * @param pfs the pfs
     * @param clazz the clazz
     * @param handler the handler
     * @return the result list
     * @throws Exception the exception
     */
    public <T extends HasId> ResultList<String> findIds(QueryParameter query, PfsParameter pfs, Class<T> clazz, String handler) throws Exception;

    /**
     * Find total.
     *
     * @param <T> the
     * @param query the query
     * @param pfs the pfs
     * @param clazz the clazz
     * @param handler the handler
     * @return the int
     * @throws Exception the exception
     */
    public <T extends HasId> int findTotal(QueryParameter query, PfsParameter pfs, Class<T> clazz, String handler) throws Exception;

    /**
     * Find.
     *
     * @param <T> the
     * @param query the query
     * @param pfs the pfs
     * @param clazz the clazz
     * @param handler the handler
     * @return the result list
     * @throws Exception the exception
     */
    public <T extends HasId> ResultList<T> find(String query, PfsParameter pfs, Class<T> clazz, String handler) throws Exception;

    /**
     * Find.
     *
     * @param <T> the
     * @param query the query
     * @param pfs the pfs
     * @param clazz the clazz
     * @param handler the handler
     * @return the result list
     * @throws Exception the exception
     */
    public <T extends HasId> ResultList<String> findIds(String query, PfsParameter pfs, Class<T> clazz, String handler) throws Exception;

    /**
     * Find total.
     *
     * @param <T> the
     * @param query the query
     * @param pfs the pfs
     * @param clazz the clazz
     * @param handler the handler
     * @return the int
     * @throws Exception the exception
     */
    public <T extends HasId> int findTotal(String query, PfsParameter pfs, Class<T> clazz, String handler) throws Exception;

    /**
     * Find single.
     *
     * @param <T> the
     * @param query the query
     * @param clazz the clazz
     * @param handler the handler
     * @return the t
     * @throws Exception the exception
     */
    public <T extends HasModified> T findSingle(QueryParameter query, Class<T> clazz, String handler) throws Exception;

    /**
     * Find single.
     *
     * @param <T> the
     * @param query the query
     * @param clazz the clazz
     * @param handler the handler
     * @return the t
     * @throws Exception the exception
     */
    public <T extends HasModified> T findSingle(String query, Class<T> clazz, String handler) throws Exception;

    /**
     * Adds the model object.
     *
     * @param <T> the
     * @param object the model object
     * @return the t
     * @throws Exception the exception
     */
    public <T extends HasModified> T add(T object) throws Exception;

    /**
     * Adds the object.
     *
     * @param <T> the
     * @param object the object
     * @return the t
     * @throws Exception the exception
     */
    public <T extends Object> T addObject(final T object) throws Exception;

    /**
     * Update model object.
     *
     * @param <T> the
     * @param object the model object
     * @return the t
     * @throws Exception the exception
     */
    public <T extends HasModified> T update(T object) throws Exception;

    /**
     * Update object.
     *
     * @param <T> the
     * @param object the object
     * @throws Exception the exception
     */
    public <T extends Object> void updateObject(T object) throws Exception;

    /**
     * Removes the model object.
     *
     * @param <T> the
     * @param object the model object
     * @throws Exception the exception
     */
    public <T extends HasModified> void remove(T object) throws Exception;

    /**
     * Removes the object.
     *
     * @param <T> the
     * @param object the object
     * @throws Exception the exception
     */
    public <T extends Object> void removeObject(T object) throws Exception;

    /**
     * Returns the handlers.
     *
     * @param <T> the
     * @param type the type
     * @return the handlers
     * @throws Exception the exception
     */
    public <T extends SearchHandler> List<T> getHandlers(Class<T> type) throws Exception;

    /**
     * Returns the handler.
     *
     * @param <T> the
     * @param type the type
     * @return the handler
     * @throws Exception the exception
     */
    public <T extends SearchHandler> T getHandler(Class<T> type) throws Exception;

    /**
     * Returns the handler.
     *
     * @param <T> the
     * @param name the name
     * @param type the type
     * @return the handler
     * @throws Exception the exception
     */
    public <T extends SearchHandler> T getHandlerByName(String name, Class<T> type) throws Exception;

    /**
     * Compute lucene indexes.
     *
     * @param indexedObjects the indexed objects
     * @throws Exception the exception
     */
    public void computeLuceneIndexes(String indexedObjects) throws Exception;

    /**
     * Clear lucene indexes.
     *
     * @throws Exception the exception
     */
    public void clearLuceneIndexes() throws Exception;

    /**
     * Returns the entity manager.
     *
     * @return the entity manager
     * @throws Exception the exception
     */
    public EntityManager getEntityManager() throws Exception;

    /**
     * Returns the prefix to look for when selecting DB properties.
     *
     * @return the DbPrefix
     * @throws Exception the exception
     */
    public String getDbPrefix() throws Exception;

    /**
     * Check cache.
     *
     * @param cache the cache
     * @param key the key
     * @return the string
     * @throws Exception the exception
     */
    public String checkCache(String cache, String key) throws Exception;

    /**
     * Adds the cache.
     *
     * @param cache the cache
     * @param key the key
     * @param value the value
     * @throws Exception the exception
     */
    public void addCache(String cache, String key, String value) throws Exception;

    /**
     * Gets the transaction per operation.
     *
     * @return the transaction per operation
     * @throws Exception the exception
     */
    public boolean getTransactionPerOperation() throws Exception;

    /**
     * Sets the transaction per operation.
     *
     * @param transactionPerOperation the new transaction per operation
     * @throws Exception the exception
     */
    public void setTransactionPerOperation(boolean transactionPerOperation) throws Exception;

    /**
     * Commit.
     *
     * @throws Exception the exception
     */
    public void commit() throws Exception;

    /**
     * Rollback.
     *
     * @throws Exception the exception
     */
    public void rollback() throws Exception;

    /**
     * Begin transaction.
     *
     * @throws Exception the exception
     */
    public void beginTransaction() throws Exception;

    /**
     * Closes the manager.
     *
     * @throws Exception the exception
     */
    @Override
    public void close() throws Exception;

    /**
     * Clears the manager.
     *
     * @throws Exception the exception
     */
    public void clear() throws Exception;

    /**
     * Commit clear begin transaction.
     *
     * @throws Exception the exception
     */
    public void commitClearBegin() throws Exception;

    /**
     * Log and commit.
     *
     * @param objectCt the object ct
     * @param logCt the log ct
     * @param commitCt the commit ct
     * @throws Exception the exception
     */
    public void logAndCommit(int objectCt, int logCt, int commitCt) throws Exception;

}
