/*
 * (C) Copyright 2006-2007 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 *
 * $Id: MemoryDirectory.java 30381 2008-02-20 20:12:09Z gracinet $
 */

package org.nuxeo.ecm.directory.memory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.AbstractDirectory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.runtime.api.Framework;

/**
 * @author Florent Guillaume
 */
public class MemoryDirectory extends AbstractDirectory {

    public final String schemaName;

    public final Set<String> schemaSet;

    public final String idField;

    public final String passwordField;

    public Map<String, Object> map;

    public MemoryDirectorySession session;

    protected boolean isReadOnly = false;

    public MemoryDirectory(String name, String schema, String idField, String passwordField) throws DirectoryException {
        this(name, schema, new HashSet<String>(), idField, passwordField);

        SchemaManager sm = getSchemaManager();
        Schema sch = sm.getSchema(schema);
        if (sch == null) {
            throw new DirectoryException("Unknown schema :" + schema);
        }
        Collection<Field> fields = sch.getFields();
        for (Field f : fields) {
            schemaSet.add(f.getName().getLocalName());
        }
    }

    public SchemaManager getSchemaManager() throws DirectoryException {
        SchemaManager sm = Framework.getService(SchemaManager.class);
        if (sm == null) {
            throw new DirectoryException("Unable to look up type service");
        }
        return sm;
    }

    public MemoryDirectory(String name, String schemaName, Set<String> schemaSet, String idField, String passwordField) {
        super(name);
        this.schemaName = schemaName;
        this.schemaSet = schemaSet;
        this.idField = idField;
        this.passwordField = passwordField;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSchema() {
        return schemaName;
    }

    @Override
    public String getParentDirectory() {
        return null;
    }

    @Override
    public String getIdField() {
        return idField;
    }

    @Override
    public String getPasswordField() {
        return passwordField;
    }

    @Override
    public Session getSession() {
        if (session == null) {
            session = new MemoryDirectorySession(this);
        }
        addSession(session);
        return session;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        session = null;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }

}
