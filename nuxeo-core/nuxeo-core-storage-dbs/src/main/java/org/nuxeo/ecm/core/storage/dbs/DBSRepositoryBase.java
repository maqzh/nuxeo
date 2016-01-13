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

import static java.lang.Boolean.FALSE;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.NamingException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.model.Document;
import org.nuxeo.ecm.core.model.LockManager;
import org.nuxeo.ecm.core.model.Session;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.CompositeType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.storage.FulltextConfiguration;
import org.nuxeo.ecm.core.storage.FulltextDescriptor;
import org.nuxeo.ecm.core.storage.lock.LockManagerService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Provides sharing behavior for repository sessions and other basic functions.
 *
 * @since 5.9.4
 */
public abstract class DBSRepositoryBase implements DBSRepository {

    private static final Log log = LogFactory.getLog(DBSRepositoryBase.class);

    public static final String TYPE_ROOT = "Root";

    // change to have deterministic pseudo-UUID generation for debugging
    protected final boolean DEBUG_UUIDS = false;

    private static final String UUID_ZERO = "00000000-0000-0000-0000-000000000000";

    private static final String UUID_ZERO_DEBUG = "UUID_0";

    protected final String repositoryName;

    protected final FulltextConfiguration fulltextConfiguration;

    protected final BlobManager blobManager;

    protected LockManager lockManager;

    /**
     * @since 7.4 : used to know if the LockManager was provided by this repository or externally
     */
    protected boolean selfRegisteredLockManager = false;

    public DBSRepositoryBase(String repositoryName, FulltextDescriptor fulltextDescriptor) {
        this.repositoryName = repositoryName;
        if (fulltextDescriptor.getFulltextDisabled()) {
            fulltextConfiguration = null;
        } else {
            fulltextConfiguration = new FulltextConfiguration(fulltextDescriptor);
        }
        blobManager = Framework.getService(BlobManager.class);
        initBlobsPaths();
        initLockManager();
    }

    @Override
    public void shutdown() {
        if (selfRegisteredLockManager) {
            LockManagerService lms = Framework.getService(LockManagerService.class);
            if (lms != null) {
                lms.unregisterLockManager(getLockManagerName());
            }
        }
    }

    @Override
    public String getName() {
        return repositoryName;
    }

    @Override
    public FulltextConfiguration getFulltextConfiguration() {
        return fulltextConfiguration;
    }

    protected String getLockManagerName() {
        // TODO configure in repo descriptor
        return getName();
    }

    protected void initLockManager() {
        String lockManagerName = getLockManagerName();
        LockManagerService lockManagerService = Framework.getService(LockManagerService.class);
        lockManager = lockManagerService.getLockManager(lockManagerName);
        if (lockManager == null) {
            // no descriptor, use DBS repository intrinsic lock manager
            lockManager = this;
            log.info("Repository " + repositoryName + " using own lock manager");
            lockManagerService.registerLockManager(lockManagerName, lockManager);
            selfRegisteredLockManager = true;
        } else {
            selfRegisteredLockManager = false;
            log.info("Repository " + repositoryName + " using lock manager " + lockManager);
        }
    }

    @Override
    public LockManager getLockManager() {
        return lockManager;
    }

    protected abstract void initBlobsPaths();

    /** Finds the paths for all blobs in all document types. */
    protected static abstract class BlobFinder {

        protected final Set<String> schemaDone = new HashSet<>();

        protected final Deque<String> path = new ArrayDeque<>();

        public void visit() {
            SchemaManager schemaManager = Framework.getService(SchemaManager.class);
            // document types
            for (DocumentType docType : schemaManager.getDocumentTypes()) {
                visitSchemas(docType.getSchemas());
            }
            // mixins
            for (CompositeType type : schemaManager.getFacets()) {
                visitSchemas(type.getSchemas());
            }
        }

        protected void visitSchemas(Collection<Schema> schemas) {
            for (Schema schema : schemas) {
                if (schemaDone.add(schema.getName())) {
                    visitComplexType(schema);
                }
            }
        }

        protected void visitComplexType(ComplexType complexType) {
            if (TypeConstants.isContentType(complexType)) {
                recordBlobPath();
                return;
            }
            for (Field field : complexType.getFields()) {
                visitField(field);
            }
        }

        /** Records a blob path, stored in the {@link #path} field. */
        protected abstract void recordBlobPath();

        protected void visitField(Field field) {
            Type type = field.getType();
            if (type.isSimpleType()) {
                // scalar
                // assume no bare binary exists
            } else if (type.isComplexType()) {
                // complex property
                String name = field.getName().getPrefixedName();
                path.addLast(name);
                visitComplexType((ComplexType) type);
                path.removeLast();
            } else {
                // array or list
                Type fieldType = ((ListType) type).getFieldType();
                if (fieldType.isSimpleType()) {
                    // array
                    // assume no array of bare binaries exist
                } else {
                    // complex list
                    String name = field.getName().getPrefixedName();
                    path.addLast(name);
                    visitComplexType((ComplexType) fieldType);
                    path.removeLast();
                }
            }
        }
    }

    /**
     * Initializes the root and its ACP.
     */
    public void initRoot() {
        Session session = getSession();
        Document root = session.importDocument(getRootId(), null, "", TYPE_ROOT, new HashMap<String, Serializable>());
        ACLImpl acl = new ACLImpl();
        acl.add(new ACE(SecurityConstants.ADMINISTRATORS, SecurityConstants.EVERYTHING, true));
        acl.add(new ACE(SecurityConstants.ADMINISTRATOR, SecurityConstants.EVERYTHING, true));
        acl.add(new ACE(SecurityConstants.MEMBERS, SecurityConstants.READ, true));
        ACPImpl acp = new ACPImpl();
        acp.addACL(acl);
        session.setACP(root, acp, true);
        session.save();
        session.close();
        if (TransactionHelper.isTransactionActive()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    @Override
    public String getRootId() {
        return DEBUG_UUIDS ? UUID_ZERO_DEBUG : UUID_ZERO;
    }

    @Override
    public BlobManager getBlobManager() {
        return blobManager;
    }

    @Override
    public boolean isFulltextDisabled() {
        return fulltextConfiguration == null;
    }

    @Override
    public int getActiveSessionsCount() {
        return transactionContexts.size();
    }

    @Override
    public Session getSession() {
        Transaction transaction;
        try {
            transaction = TransactionHelper.lookupTransactionManager().getTransaction();
            if (transaction != null && transaction.getStatus() != Status.STATUS_ACTIVE) {
                transaction = null;
            }
        } catch (SystemException | NamingException e) {
            transaction = null;
        }

        if (transaction == null) {
            // no active transaction, use a regular session
            return newSession();
        }

        TransactionContext context = transactionContexts.get(transaction);
        if (context == null) {
            context = new TransactionContext(transaction, newSession());
            context.init();
        }
        return context.newSession();
    }

    protected DBSSession newSession() {
        return new DBSSession(this);
    }

    public Map<Transaction, TransactionContext> transactionContexts = new ConcurrentHashMap<>();

    /**
     * Context maintained during a transaction, holding the base session used, and all session proxy handles that have
     * been returned to callers.
     */
    public class TransactionContext implements Synchronization {

        protected final Transaction transaction;

        protected final DBSSession baseSession;

        protected final Set<Session> proxies;

        public TransactionContext(Transaction transaction, DBSSession baseSession) {
            this.transaction = transaction;
            this.baseSession = baseSession;
            proxies = new HashSet<>();
        }

        public void init() {
            transactionContexts.put(transaction, this);
            begin();
            // make sure it's closed (with handles) at transaction end
            try {
                transaction.registerSynchronization(this);
            } catch (RollbackException | SystemException e) {
                throw new RuntimeException(e);
            }
        }

        public Session newSession() {
            ClassLoader cl = getClass().getClassLoader();
            DBSSessionInvoker invoker = new DBSSessionInvoker(this);
            Session proxy = (Session) Proxy.newProxyInstance(cl, new Class[] { Session.class }, invoker);
            add(proxy);
            return proxy;
        }

        public void add(Session proxy) {
            proxies.add(proxy);
        }

        public boolean remove(Object proxy) {
            return proxies.remove(proxy);
        }

        public void begin() {
            baseSession.begin();
        }

        @Override
        public void beforeCompletion() {
        }

        @Override
        public void afterCompletion(int status) {
            if (status == Status.STATUS_COMMITTED) {
                baseSession.commit();
            } else if (status == Status.STATUS_ROLLEDBACK) {
                baseSession.rollback();
            } else {
                log.error("Unexpected afterCompletion status: " + status);
            }
            baseSession.close();
            removeTransaction();
        }

        protected void removeTransaction() {
            for (Session proxy : proxies.toArray(new Session[0])) {
                proxy.close(); // so that users of the session proxy see it's not live anymore
            }
            transactionContexts.remove(transaction);
        }
    }

    /**
     * An indirection to a base {@link DBSSession} intercepting {@code close()} to not close the base session until the
     * transaction itself is closed.
     */
    public static class DBSSessionInvoker implements InvocationHandler {

        private static final String METHOD_HASHCODE = "hashCode";

        private static final String METHOD_EQUALS = "equals";

        private static final String METHOD_CLOSE = "close";

        private static final String METHOD_ISLIVE = "isLive";

        protected final TransactionContext context;

        protected boolean closed;

        public DBSSessionInvoker(TransactionContext context) {
            this.context = context;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (methodName.equals(METHOD_HASHCODE)) {
                return doHashCode();
            }
            if (methodName.equals(METHOD_EQUALS)) {
                return doEquals(args);
            }
            if (methodName.equals(METHOD_CLOSE)) {
                return doClose(proxy);
            }
            if (methodName.equals(METHOD_ISLIVE)) {
                return doIsLive();
            }

            if (closed) {
                throw new NuxeoException("Cannot use closed connection handle");
            }

            try {
                return method.invoke(context.baseSession, args);
            } catch (ReflectiveOperationException e) {
                throw ExceptionUtils.unwrapInvoke(e);
            }
        }

        protected Integer doHashCode() {
            return Integer.valueOf(hashCode());
        }

        protected Boolean doEquals(Object[] args) {
            if (args.length != 1 || args[0] == null) {
                return FALSE;
            }
            Object other = args[0];
            if (!(Proxy.isProxyClass(other.getClass()))) {
                return FALSE;
            }
            InvocationHandler otherInvoker = Proxy.getInvocationHandler(other);
            return Boolean.valueOf(equals(otherInvoker));
        }

        protected Object doClose(Object proxy) {
            closed = true;
            context.remove(proxy);
            return null;
        }

        protected Boolean doIsLive() {
            if (closed) {
                return FALSE;
            } else {
                return Boolean.valueOf(context.baseSession.isLive());
            }
        }
    }

}
