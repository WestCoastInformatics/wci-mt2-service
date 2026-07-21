/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

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
import javax.persistence.Query;

import org.hibernate.CacheMode;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.schema.management.SearchSchemaManager;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.util.common.SearchException;
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
public class TerminologyService implements RootService {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(TerminologyService.class);

	/** The last modified flag. */
	private boolean lastModifiedFlag = true;

	/** The last modified by. */
	private String lastModifiedBy = null;

	/** The reindex. */
	private static boolean reindex = true;

	/**
	 * Skip the one-time startup reindex (e.g. after it already ran before Spring Boot started).
	 */
	public static void disableStartupReindex() {

		reindex = false;
	}

	/**
	 * Ensure the static entity manager factory matches the class loader of the anchor type.
	 *
	 * @param anchorClass class whose class loader should be used for the factory
	 * @throws Exception the exception
	 */
	public static void ensureFactoryForClassLoader(final Class<?> anchorClass) throws Exception {

		final ClassLoader anchorClassLoader = anchorClass.getClassLoader();
		if (factory == null || !factory.isOpen() || factoryClassLoader != anchorClassLoader) {
			if (factory != null && factory.isOpen()) {
				factory.close();
			}
			factory = Persistence.createEntityManagerFactory("refsetservice-ds", PropertyUtility.getJpaProperties());
			factoryClassLoader = anchorClassLoader;
		}
	}

	/** The search handler. */
	private static Map<String, SearchHandler> searchHandlerMap = new HashMap<>();

	/** The factory. */
	private static EntityManagerFactory factory = null;

	/** The class loader used to create the factory. */
	private static ClassLoader factoryClassLoader = null;

	/** The manager. */
	private EntityManager manager;

	/** The transaction per operation. */
	private boolean transactionPerOperation = true;

	/** Should multiple transactions per operation be automatically allowed. */
	private boolean autoMultipleTransactionsPerOperation = true;

	/** The transaction per operation internally changed. */
	private boolean transactionPerOperationIntenallyChanged = false;

	/** The transaction entity. */
	private EntityTransaction transaction;

	/** The model package. */
	private static String modelPackage = "org.ihtsdo.refsetservice.model";

	/** The model package 2. */
	private static String modelPackage2 = "org.ihtsdo.refsetservice.model.data";

	/** Configuration properties. */
	private Properties properties;

	/**
	 * Instantiates an empty {@link TerminologyService}.
	 *
	 * @throws Exception the exception
	 */
	public TerminologyService() throws Exception {

		if (properties == null) {
			properties = PropertyUtility.getProperties();
		}

		// created once or if the factory has closed or the class loader changed (devtools restart)
		ensureFactoryForClassLoader(getClass());

		if (searchHandlerMap == null) {
			searchHandlerMap = new HashMap<>();
		}

		if (searchHandlerMap.isEmpty()) {

			final String key = "search.handler";
			LOG.debug(">>>>>> handler property: " + PropertyUtility.getProperty(key));

			for (final String handlerName : PropertyUtility.getProperty(key).split(",")) {
				LOG.debug(">>>>>> handler name: " + handlerName);
				if (handlerName.isEmpty()) {
					continue;
				}

				// Add handlers to map
				final SearchHandler handlerService = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key,
						handlerName, SearchHandler.class);
				searchHandlerMap.put(handlerName, handlerService);
			}

			LOG.debug(">>>>>> searchHandlerMap: " + ModelUtility.toJson(searchHandlerMap));

			if (!searchHandlerMap.containsKey(ModelUtility.DEFAULT)) {
				throw new Exception("search.handler." + ModelUtility.DEFAULT + " expected and does not exist.");
			}

			LOG.debug("  initialize search handler = "
					+ searchHandlerMap.values().stream().map(f -> f.getName()).collect(Collectors.toSet()));

			// Validate the search handler map was initialized successfully
			validateInit();
		}

		// created on each instantiation
		manager = factory.createEntityManager();
		transaction = manager.getTransaction();

		// If we're using DDL "create" mode, clear the indexes
		if (reindex || "create".equals(properties.getProperty("hibernate.hbm2ddl.auto"))) {
			LOG.info("  clear indexes");
			clearLuceneIndexes();
			computeLuceneIndexes(null);
			reindex = false;
		}
	}

	/**
	 * Validate init.
	 *
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public void validateInit() throws Exception {

		// Verify everything initialized properly
		if (searchHandlerMap == null) {
			throw new Exception("Unable to initialize root service");
		}
	}

	/**
	 * Gets the type.
	 *
	 * @param type the type
	 * @return the type
	 * @throws Exception the exception
	 */
	/* see superclass */
	@SuppressWarnings("unchecked")
	@Override
	public Class<? extends HasModified> getType(final String type) throws Exception {

		try {
			return (Class<? extends HasModified>) Class.forName(modelPackage + "." + StringUtility.capitalize(type));
		} catch (final Exception e) {
			return (Class<? extends HasModified>) Class.forName(modelPackage2 + "." + StringUtility.capitalize(type));

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
			LOG.debug("Setting root service entity manager factory.");
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

		if (transactionPerOperation && autoMultipleTransactionsPerOperation) {

			transactionPerOperation = false;
			transactionPerOperationIntenallyChanged = true;

		} else if (transaction != null && transaction.isActive()) {
			throw new IllegalStateException(
					"Error attempting to begin a transaction when there " + "is already an active transaction");
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

		if (transactionPerOperation) {
			throw new IllegalStateException(
					"Error attempting to commit a transaction when using transactions per operation mode.");

		} else if (transaction != null && !transaction.isActive()) {
			throw new IllegalStateException(
					"Error attempting to commit a transaction when there is no active transaction");

		} else if (transaction != null) {

			transaction.commit();
			manager.clear();

			if (transactionPerOperationIntenallyChanged) {

				transactionPerOperationIntenallyChanged = false;
				transactionPerOperation = true;
			}
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
					"Error attempting to rollback a transaction when using transactions per " + " operation mode.");
		} else if (transaction != null && !transaction.isActive()) {
			LOG.debug("n/a");
		} else if (transaction != null) {
			transaction.rollback();
			manager.clear();
		}
	}

	/**
	 * Close.
	 *
	 * @throws Exception the exception
	 */
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
	 * @param pfs      the pfs
	 * @return the javax.persistence. query
	 * @throws Exception the exception
	 */
	public javax.persistence.Query applyPfsToJPQLQuery(final String queryStr, final PfsParameter pfs) throws Exception {

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
	 * @param o         the object
	 * @param sortField the period-separated X list of sequential getX methods, e.g.
	 *                  a.b.c
	 * @return the value of the requested sort field
	 * @throws Exception the exception
	 */
	public Object getSortFieldValue(final Object o, final String sortField) throws Exception {

		// split the fields for method retrieval, e.g. a.b.c. =
		// o.getA().getB().getC()
		final String[] splitFields = sortField.split("\\.");

		int i = 0;
		Method finalMethod = null;
		Object finalObject = o;

		while (i < splitFields.length) {
			finalMethod = finalObject.getClass().getMethod("get" + StringUtility.capitalize(splitFields[i]),
					new Class<?>[] {});
			finalMethod.setAccessible(true);
			finalObject = finalMethod.invoke(finalObject, new Object[] {});
			i++;
		}

		if (finalMethod == null) {
			throw new Exception("Missing get method for sort field " + sortField);
		}

		// verify that final object is actually a string, enum, or date
		if (!finalMethod.getReturnType().equals(String.class) && !finalMethod.getReturnType().isEnum()
				&& !finalMethod.getReturnType().equals(Long.class) && !finalMethod.getReturnType().equals(Date.class)) {
			throw new Exception("Requested sort field value is not string, enum, or date value "
					+ finalMethod.getReturnType().getName());
		}
		return finalObject;

	}

	/**
	 * Retrieves the sort field value from an object.
	 *
	 * @param o         the object
	 * @param sortField the period-separated X list of sequential getX methods, e.g.
	 *                  a.b.c
	 * @return the value of the requested sort field
	 * @throws Exception the exception
	 */
	// package visibility
	protected Class<?> getSortFieldType(final Object o, final String sortField) throws Exception {

		// split the fields for method retrieval, e.g. a.b.c. =
		// o.getA().getB().getC()
		final String[] splitFields = sortField.split("\\.");

		int i = 0;
		Method finalMethod = null;
		Object finalObject = o;

		while (i < splitFields.length) {
			finalMethod = finalObject.getClass().getMethod("get" + StringUtility.capitalize(splitFields[i]),
					new Class<?>[] {});
			finalMethod.setAccessible(true);
			finalObject = finalMethod.invoke(finalObject, new Object[] {});
			i++;
		}

		if (finalMethod == null) {
			throw new Exception("Missing get method for sort field " + sortField);
		}

		// verify that final object is actually a string, enum, or date
		if (!finalMethod.getReturnType().equals(String.class) && !finalMethod.getReturnType().isEnum()
				&& !finalMethod.getReturnType().equals(Long.class) && !finalMethod.getReturnType().equals(Date.class)) {
			throw new Exception("Requested sort field value is not string, enum, or date value "
					+ finalMethod.getReturnType().getName());
		}
		return finalMethod.getReturnType();

	}

	/**
	 * Apply pfs to List.
	 *
	 * @param <T>     the
	 * @param list    the list
	 * @param clazz   the clazz
	 * @param totalCt the total ct
	 * @param pfs     the pfs
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
									final Long l1 = s1 == null ? null : (isDate ? ((Date) s1).getTime() : ((Long) s1));
									final Long l2 = s2 == null ? null : (isDate ? ((Date) s2).getTime() : ((Long) s2));

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
									if (s2 == null) {
										if (s1 == null) {
											return 0;
										}
										return -1;
									}
									if (s1 == null) {
										return 1;
									}

									if (s1.toString().compareTo(s2.toString()) != 0) {
										return s1.toString().compareTo(s2.toString());
									} else {
										return 0;
									}
								} else {
									if (s2 == null) {
										if (s1 == null) {
											return 0;
										}
										return 1;
									}
									if (s1 == null) {
										return -1;
									}
									if ((s2.toString()).compareTo(s1.toString()) != 0) {
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
			toIndex = (pfs.getLimit() == -1) ? result.size() : Math.min(result.size(), startIndex + pfs.getLimit());
			if (startIndex > toIndex) {
				startIndex = 0;
			}
			result = result.subList(startIndex, toIndex);
		}

		return result;
	}

	/**
	 * Sets the modified flag.
	 *
	 * @param lastModifiedFlag the new modified flag
	 */
	/* see superclass */
	@Override
	public void setModifiedFlag(final boolean lastModifiedFlag) {

		this.lastModifiedFlag = lastModifiedFlag;
	}

	/**
	 * Checks if is modified flag.
	 *
	 * @return true, if is modified flag
	 */
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
	 * @param logCt    the log ct
	 * @param commitCt the commit ct
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public void logAndCommit(final int objectCt, final int logCt, final int commitCt) throws Exception {

		// log at regular intervals
		if (objectCt % logCt == 0 && objectCt > 0) {
			LOG.info("    count = " + objectCt);
		}
		if (objectCt % commitCt == 0) {
			commitClearBegin();
		}
	}

	/**
	 * Adds the has last modified.
	 *
	 * @param <T>             the
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

	/**
	 * Adds the object.
	 *
	 * @param <T>    the generic type
	 * @param object the object
	 * @return the t
	 * @throws Exception the exception
	 */
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
	 * @param <T>             the
	 * @param hasLastModified the has last modified
	 * @throws Exception the exception
	 */
	public <T extends HasModified> void updateHasLastModified(final T hasLastModified) throws Exception {

		// set last modified fields (user, timestamp)
		if (isModifiedFlag()) {
			if (getModifiedBy() == null) {
				throw new Exception("Service cannot update object, name of modifying user required");
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

	/**
	 * Update object.
	 *
	 * @param <T>    the generic type
	 * @param object the object
	 * @throws Exception the exception
	 */
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
	 * @param <T>   the
	 * @param id    the id
	 * @param clazz the clazz
	 * @return the t
	 * @throws Exception the exception
	 */
	public <T extends HasModified> T removeHasLastModified(final String id, final Class<T> clazz) throws Exception {

		try {
			// Get transaction and object
			transaction = manager.getTransaction();
			final T hasLastModified = manager.find(clazz, id);

			// set last modified fields (user, timestamp)
			if (isModifiedFlag()) {
				if (getModifiedBy() == null) {
					throw new Exception("Service cannot remove object, name of modifying user required");
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

	/**
	 * Removes the object.
	 *
	 * @param <T>    the generic type
	 * @param object the object
	 * @throws Exception the exception
	 */
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
	 * @param <T>   the
	 * @param id    the id
	 * @param clazz the clazz
	 * @return the checks for object
	 * @throws Exception the exception
	 */
	protected <T extends Object> T getObject(final String id, final Class<T> clazz) throws Exception {

		// Get transaction and object
		transaction = manager.getTransaction();
		final T component = manager.find(clazz, id);
		return component;
	}

	/**
	 * Lock object.
	 *
	 * @param object the object
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public void lockObject(final Object object) throws Exception {

		manager.lock(object, LockModeType.PESSIMISTIC_WRITE);
	}

	/**
	 * Unlock object.
	 *
	 * @param object the object
	 */
	/* see superclass */
	@Override
	public void unlockObject(final Object object) {

		manager.lock(object, LockModeType.NONE);
	}

	/**
	 * Checks if is object locked.
	 *
	 * @param object the object
	 * @return true, if is object locked
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public boolean isObjectLocked(final Object object) throws Exception {

		return manager.getLockMode(object).equals(LockModeType.PESSIMISTIC_WRITE);
	}

	/**
	 * Refresh caches.
	 *
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public void refreshCaches() throws Exception {

		// init();
		closeFactory();
		openFactory();
	}

	/**
	 * Gets the modified by.
	 *
	 * @return the modified by
	 */
	/* see superclass */
	@Override
	public String getModifiedBy() {

		return lastModifiedBy;
	}

	/**
	 * Sets the modified by.
	 *
	 * @param lastModifiedBy the new modified by
	 */
	/* see superclass */
	@Override
	public void setModifiedBy(final String lastModifiedBy) {

		this.lastModifiedBy = lastModifiedBy;
	}

	/**
	 * Gets the.
	 *
	 * @param <T>   the generic type
	 * @param id    the id
	 * @param clazz the clazz
	 * @return the t
	 * @throws Exception the exception
	 */
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

	/**
	 * Gets the all.
	 *
	 * @param <T>   the generic type
	 * @param clazz the clazz
	 * @return the all
	 * @throws Exception the exception
	 */
	/* see superclass */
	@SuppressWarnings("unchecked")
	@Override
	public <T extends HasModified> List<T> getAll(final Class<T> clazz) throws Exception {

		try {
			final javax.persistence.Query query = getEntityManager().createQuery("from " + clazz.getName());
			return query.getResultList();
		} catch (final NoResultException e) {
			return null;
		}
	}

	/**
	 * Find.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param pfs     the pfs
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the result list
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasId> ResultList<T> find(final String query, final PfsParameter pfs, final Class<T> clazz,
			final String handler) throws Exception {

		return find(new QueryParameter(query), pfs, clazz, handler);
	}

	/**
	 * Find ids.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param pfs     the pfs
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the result list
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasId> ResultList<String> findIds(final String query, final PfsParameter pfs,
			final Class<T> clazz, final String handler) throws Exception {

		return findIds(new QueryParameter(query), pfs, clazz, handler);
	}

	/**
	 * Find total.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param pfs     the pfs
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the int
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasId> int findTotal(final String query, final PfsParameter pfs, final Class<T> clazz,
			final String handler) throws Exception {

		return findTotal(new QueryParameter(query), pfs, clazz, handler);
	}

	/**
	 * Find.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param pfs     the pfs
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the result list
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasId> ResultList<T> find(final QueryParameter query, final PfsParameter pfs,
			final Class<T> clazz, final String handler) throws Exception {

		final ResultList<T> list = new ResultList<>();
		final int[] totalCt = new int[1];
		if (query == null) {
			list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler, SearchHandler.class)
					.getQueryResults("*:*", null, null, clazz, pfs, totalCt, getEntityManager()));
		} else {
			list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler, SearchHandler.class)
					.getQueryResults(query.getQuery(), query.getFieldedClauses(), query.getAdditionalClauses(), clazz,
							pfs, totalCt, getEntityManager()));
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
		// LOG.error("Unexpected error unmarshalling search result", e);
		// }
		// }).count();
		return list;
	}

	/**
	 * Find ids.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param pfs     the pfs
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the result list
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasId> ResultList<String> findIds(final QueryParameter query, final PfsParameter pfs,
			final Class<T> clazz, final String handler) throws Exception {

		final ResultList<String> list = new ResultList<>();
		final int[] totalCt = new int[1];
		if (query == null) {
			list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler, SearchHandler.class)
					.getIdResults("*:*", null, null, clazz, pfs, totalCt, getEntityManager()));
		} else {
			list.setItems(getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler, SearchHandler.class)
					.getIdResults(query.getQuery(), query.getFieldedClauses(), query.getAdditionalClauses(), clazz, pfs,
							totalCt, getEntityManager()));
		}
		list.setTotal(totalCt[0]);
		list.setLimit(pfs == null ? new PfsParameter().getLimit() : pfs.getLimit());
		list.setOffset(pfs == null ? new PfsParameter().getOffset() : pfs.getOffset());
		return list;
	}

	/**
	 * Find total.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param pfs     the pfs
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the int
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasId> int findTotal(final QueryParameter query, final PfsParameter pfs, final Class<T> clazz,
			final String handler) throws Exception {

		if (query == null) {
			return getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler, SearchHandler.class)
					.countQueryResults("*:*", null, null, clazz, pfs, getEntityManager());
		}

		return getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler, SearchHandler.class)
				.countQueryResults(query.getQuery(), query.getFieldedClauses(), query.getAdditionalClauses(), clazz,
						pfs, getEntityManager());
	}

	/**
	 * Adds the.
	 *
	 * @param <T>    the generic type
	 * @param object the object
	 * @return the t
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasModified> T add(final T object) throws Exception {

		return addHasLastModified(object);
	}

	/**
	 * Update.
	 *
	 * @param <T>    the generic type
	 * @param object the object
	 * @return the t
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasModified> T update(final T object) throws Exception {

		updateHasLastModified(object);
		return object;
	}

	/**
	 * Removes the.
	 *
	 * @param <T>    the generic type
	 * @param object the object
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public <T extends HasModified> void remove(final T object) throws Exception {

		removeObject(object);
	}

	/**
	 * Gets the handlers.
	 *
	 * @param <T>  the generic type
	 * @param type the type
	 * @return the handlers
	 * @throws Exception the exception
	 */
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

	/**
	 * Gets the handler.
	 *
	 * @param <T>  the generic type
	 * @param type the type
	 * @return the handler
	 * @throws Exception the exception
	 */
	/* see superclass */
	@SuppressWarnings("unchecked")
	@Override
	public <T extends SearchHandler> T getHandler(final Class<T> type) throws Exception {

		if (type == SearchHandler.class) {
			return (T) searchHandlerMap.get(ModelUtility.DEFAULT);
		}
		return null;
	}

	/**
	 * Gets the handler by name.
	 *
	 * @param <T>  the generic type
	 * @param name the name
	 * @param type the type
	 * @return the handler by name
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	@SuppressWarnings("unchecked")
	public <T extends SearchHandler> T getHandlerByName(final String name, final Class<T> type) throws Exception {

		if (type == SearchHandler.class) {
			if (!searchHandlerMap.containsKey(name)) {
				throw new Exception("Unexpected missing handler name for DefaultSearchHandler.class = " + name);
			}
			return (T) searchHandlerMap.get(name);
		}

		return null;
	}

	/**
	 * New instance.
	 *
	 * @param <T>   the
	 * @param clazz the clazz
	 * @return the t
	 * @throws Exception the exception
	 */
	@Override
	@SuppressWarnings({ "unchecked", "deprecation" })
	public <T> T newInstance(final Class<T> clazz) throws Exception {

		final String simpleName = clazz.getSimpleName();
		final String jpaClassName = clazz.getName().replace(simpleName, "jpa." + simpleName + "Jpa");
		final Class<?> jpaClass = Class.forName(jpaClassName);
		if (jpaClass == null) {
			throw new Exception("Unable to find class " + jpaClassName);
		}
		return (T) jpaClass.newInstance();

	}

	/**
	 * Gets the type.
	 *
	 * @param <T>   the generic type
	 * @param <S>   the generic type
	 * @param clazz the clazz
	 * @return the type
	 * @throws Exception the exception
	 */
	/* see superclass */
	@SuppressWarnings("unchecked")
	@Override
	public <T extends HasModified, S extends T> Class<S> getType(final Class<T> clazz) throws Exception {

		final String simpleName = clazz.getSimpleName();
		final String jpaClassName = clazz.getName().replace(simpleName, "jpa." + simpleName + "Jpa");
		return (Class<S>) Class.forName(jpaClassName);
	}

	/**
	 * Copy instance.
	 *
	 * @param <T>    the generic type
	 * @param clazz  the clazz
	 * @param object the object
	 * @return the t
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	@SuppressWarnings("unchecked")
	public <T extends HasId> T copyInstance(final Class<T> clazz, final T object) throws Exception {

		final String simpleName = clazz.getSimpleName();
		final String jpaClassName = clazz.getName().replace(simpleName, "jpa." + simpleName + "Jpa");
		final Class<?> jpaClass = Class.forName(jpaClassName);
		if (jpaClass == null) {
			throw new Exception("Unable to find class " + jpaClassName);
		}
		final Constructor<?> jpaConstructor = jpaClass.getConstructor(clazz);
		return (T) jpaConstructor.newInstance(object);
	}

	/**
	 * Compute lucene indexes.
	 *
	 * @param indexedObjects the indexed objects
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public void computeLuceneIndexes(final String indexedObjects) throws Exception {

		// set of objects to be re-indexed
		final Set<String> objectsToReindex = new HashSet<>();
		final Map<String, Class<?>> reindexMap = new HashMap<>();
		final Reflections reflections = new Reflections(properties.getProperty("app.entity_packages"));
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
					throw new Exception("Reindex process assumes simple class names are different.");
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

		LOG.info("starting reindexing for:");
		for (final String objectToReindex : objectsToReindex) {
			LOG.info("  " + objectToReindex);
		}

		final SearchSession searchSession = Search.session(getEntityManager());
		final Set<String> indexedSimpleNames = Search.mapping(getEntityManager().getEntityManagerFactory()).allIndexedEntities().stream()
				.map(indexedEntity -> indexedEntity.javaClass().getSimpleName()).collect(Collectors.toSet());
		LOG.info("  Hibernate Search indexed entity types: {}", indexedSimpleNames);
		if (indexedSimpleNames.isEmpty()) {
			throw new Exception("Hibernate Search mapping contains no indexed entities. "
					+ "Check spring.jpa.properties.hibernate.search.* (backend type/hosts) and that the EntityManagerFactory was created with those properties.");
		}

		// Reindex each object
		for (final String key : reindexMap.keySet()) {
			// Concepts
			if (objectsToReindex.contains(key)) {
				LOG.info("  creating indexes for " + key);

				try {
					// Use the JPA-managed Class instance so massIndexer matches the Search mapping
					// (avoids ClassLoader mismatches, e.g. spring-boot-devtools RestartClassLoader).
					final Class<?> indexedClass = resolveManagedIndexedClass(key, reindexMap.get(key));
					searchSession.workspace(indexedClass).purge();
					searchSession.indexingPlan().execute(); // may not need
															// anymore
					searchSession.massIndexer(indexedClass).batchSizeToLoadObjects(100)
							.cacheMode(CacheMode.IGNORE).idFetchSize(100).threadsToLoadObjects(10).startAndWait();
				} catch (final IllegalArgumentException e) {
					LOG.warn("      NOT AN ENTITY in this project");
					// throw new Exception (e);
				} catch (final SearchException e) {
					LOG.warn("      Type not in search mapping, skipping: " + key, e);
					objectsToReindex.remove(key);
				}

				// if using elasticsearch the max result window size must be
				// increased
				if (properties.getProperty("spring.jpa.properties.hibernate.search.backend.type").trim()
						.equals("elasticsearch")) {
					IndexUtility.setMaxWindowSize(key, getEntityManager());
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

	/**
	 * Resolves the JPA-managed Class for an indexed type by simple name.
	 * Prefer the metamodel Class so it matches the Hibernate Search mapping ClassLoader.
	 *
	 * @param simpleName the entity simple class name
	 * @param fallback the Class discovered via classpath scanning
	 * @return the managed Class to pass to massIndexer
	 * @throws Exception if the type is not a managed entity
	 */
	private Class<?> resolveManagedIndexedClass(final String simpleName, final Class<?> fallback) throws Exception {

		for (final javax.persistence.metamodel.EntityType<?> entityType : getEntityManager().getMetamodel().getEntities()) {
			final Class<?> javaType = entityType.getJavaType();
			if (javaType != null && javaType.getSimpleName().equals(simpleName)) {
				return javaType;
			}
		}
		if (fallback != null) {
			return fallback;
		}
		throw new Exception("No managed entity found for indexed type: " + simpleName);
	}

	/**
	 * Clear lucene indexes.
	 *
	 * @throws Exception the exception
	 */
	/* see superclass */
	@Override
	public void clearLuceneIndexes() throws Exception {

		LOG.info("  clearing lucene indexes");

		LOG.info("******** properties app.entity_packages: " + properties.getProperty("app.entity_packages"));
		// NUNO DEAD CODE final Reflections reflections = new
		// Reflections(properties.getProperty("app.entity_packages"));
		final SearchSession searchSession = Search.session(getEntityManager());

		final SearchSchemaManager schemaManager = searchSession.schemaManager();
		schemaManager.dropAndCreate();

	}

	/**
	 * Find single.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the t
	 * @throws Exception the exception
	 */
	@Override
	public <T extends HasModified> T findSingle(final QueryParameter query, final Class<T> clazz, final String handler)
			throws Exception {

		final PfsParameter pfs = new PfsParameter();
		pfs.setOffset(0);
		pfs.setLimit(2);
		if (query == null) {
			return null;
		}
		final List<T> list = getHandlerByName(StringUtility.isEmpty(handler) ? "DEFAULT" : handler, SearchHandler.class)
				.getQueryResults(query.getQuery(), query.getFieldedClauses(), query.getAdditionalClauses(), clazz, pfs,
						new int[1], getEntityManager());
		if (list.size() == 0) {
			return null;
		}
		if (list.size() == 1) {
			return list.get(0);
		}
		throw new Exception("More than one object returned for the query - " + list.size() + ", " + list.toString());
	}

	/**
	 * Find single.
	 *
	 * @param <T>     the generic type
	 * @param query   the query
	 * @param clazz   the clazz
	 * @param handler the handler
	 * @return the t
	 * @throws Exception the exception
	 */
	@Override
	public <T extends HasModified> T findSingle(final String query, final Class<T> clazz, final String handler)
			throws Exception {

		return findSingle(new QueryParameter(query), clazz, handler);
	}

	/**
	 * Gets the db prefix.
	 *
	 * @return the db prefix
	 * @throws Exception the exception
	 */
	@Override
	public String getDbPrefix() throws Exception {

		return null;
	}

	/**
	 * Check cache.
	 *
	 * @param cache the cache
	 * @param key   the key
	 * @return the string
	 * @throws Exception the exception
	 */
	@Override
	public String checkCache(final String cache, final String key) throws Exception {

		return null;
	}

	/**
	 * Adds the cache.
	 *
	 * @param cache the cache
	 * @param key   the key
	 * @param value the value
	 * @throws Exception the exception
	 */
	@Override
	public void addCache(final String cache, final String key, final String value) throws Exception {

		// n/a

	}

	/**
	 * Clear user sessions.
	 *
	 * @throws Exception the exception
	 */
	public void clearUserSessions() throws Exception {

		this.setTransactionPerOperation(false);
		this.beginTransaction();

		final Query query1 = manager.createNativeQuery("DELETE from spring_session_attributes");
		query1.executeUpdate();

		final Query query2 = manager.createNativeQuery("DELETE from spring_session");
		query2.executeUpdate();

		this.commit();

	}

}
