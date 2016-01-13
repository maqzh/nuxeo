/*
 * Copyright (c) 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.dbs;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.model.LockManager;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.storage.FulltextConfiguration;
import org.nuxeo.ecm.core.storage.State;
import org.nuxeo.ecm.core.storage.State.StateDiff;

/**
 * Interface for a {@link Repository} for Document-Based Storage.
 *
 * @since 5.9.4
 */
public interface DBSRepository extends Repository, LockManager {

    /**
     * Gets the blob manager.
     *
     * @return the blob manager.
     */
    BlobManager getBlobManager();

    /**
     * Gets the fulltext configuration.
     *
     * @return the fulltext configuration
     *
     * @since 7.10-HF04, 8.1
     */
    FulltextConfiguration getFulltextConfiguration();

    /**
     * Checks if fulltext indexing is disabled.
     *
     * @return {@code true} if fulltext indexing is disabled, {@code false} if it is enabled
     * @since 7.1, 6.0-HF02
     */
    boolean isFulltextDisabled();

    /**
     * Gets the root id.
     *
     * @return the root id.
     */
    String getRootId();

    /**
     * Generates a new id for a document.
     *
     * @return the new id
     */
    String generateNewId();

    /**
     * Reads the state of a document.
     *
     * @param id the document id
     * @return the document state, or {@code null} if not found
     */
    State readState(String id);

    /**
     * Reads the states of several documents.
     * <p>
     * The returned states may be in a different order than the ids.
     *
     * @param ids the document ids
     * @return the document states, an element by be {@code null} if not found
     */
    List<State> readStates(List<String> ids);

    /**
     * Creates a document.
     *
     * @param state the document state
     */
    void createState(State state);

    /**
     * Updates a document.
     *
     * @param id the document id
     * @param diff the diff to apply
     */
    void updateState(String id, StateDiff diff);

    /**
     * Deletes a set of document.
     *
     * @param ids the document ids
     */
    void deleteStates(Set<String> ids);

    /**
     * Reads the state of a child document.
     *
     * @param parentId the parent document id
     * @param name the name of the child
     * @param ignored a set of document ids that should not be considered
     * @return the state of the child document, or {@code null} if not found
     */
    State readChildState(String parentId, String name, Set<String> ignored);

    /**
     * Checks if a document has a child with the given name
     *
     * @param parentId the parent document id
     * @param name the name of the child
     * @param ignored a set of document ids that should not be considered
     * @return {@code true} if the child exists, {@code false} if not
     */
    boolean hasChild(String parentId, String name, Set<String> ignored);

    /**
     * Queries the repository for documents having key = value.
     *
     * @param key the key
     * @param value the value
     * @param ignored a set of document ids that should not be considered
     * @return the document states matching the query
     */
    List<State> queryKeyValue(String key, Object value, Set<String> ignored);

    /**
     * Queries the repository for documents having key1 = value1 and key2 = value2.
     *
     * @param key1 the first key
     * @param value1 the first value
     * @param key2 the second key
     * @param value2 the second value
     * @param ignored a set of document ids that should not be considered
     * @return the document states matching the query
     */
    List<State> queryKeyValue(String key1, Object value1, String key2, Object value2, Set<String> ignored);

    /**
     * Queries the repository for document ids having value in key (an array).
     *
     * @param key the key
     * @param value the value
     * @param ids the set which receives the documents ids
     * @param proxyTargets returns a map of proxy to target among the documents found
     * @param targetProxies returns a map of target to proxies among the document found
     */
    void queryKeyValueArray(String key, Object value, Set<String> ids, Map<String, String> proxyTargets,
            Map<String, Object[]> targetProxies);

    /**
     * Queries the repository to check if there are documents having key = value.
     *
     * @param key the key
     * @param value the value
     * @param ignored a set of document ids that should not be considered
     * @return {@code true} if the query matches at least one document, {@code false} if the query matches nothing
     */
    boolean queryKeyValuePresence(String key, String value, Set<String> ignored);

    /**
     * Queries the repository for documents matching a NXQL query, and returns a projection of the documents.
     *
     * @param evaluator the map-based evaluator for the query
     * @param orderByClause an ORDER BY clause
     * @param limit the limit on the number of documents to return
     * @param offset the offset in the list of documents to return
     * @param countUpTo if {@code -1}, count the total size without offset/limit.<br>
     *            If {@code 0}, don't count the total size, set it to {@code -1} .<br>
     *            If {@code n}, count the total number if there are less than n documents otherwise set the total size
     *            to {@code -2}.
     * @return a partial list of maps containing the NXQL projections requested, and the total size according to
     *         countUpTo
     */
    PartialList<Map<String, Serializable>> queryAndFetch(DBSExpressionEvaluator evaluator, OrderByClause orderByClause,
            int limit, int offset, int countUpTo);

    /**
     * Gets the lock manager for this repository.
     *
     * @return the lock manager
     * @since 7.4
     */
    LockManager getLockManager();

}
