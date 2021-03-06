/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *     Florent Guillaume
 */
package org.nuxeo.ecm.directory;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * Basic directory descriptor, containing the basic fields used by all directories.
 *
 * @since 8.2
 */
@XObject(value = "directory")
public class BaseDirectoryDescriptor implements Cloneable {

    public static final int CACHE_TIMEOUT_DEFAULT = 0;

    public static final int CACHE_MAX_SIZE_DEFAULT = 0;

    public static final boolean READ_ONLY_DEFAULT = false;

    @XNode("@name")
    public String name;

    @XNode("@remove")
    public boolean remove = false;

    @XNode("parentDirectory")
    public String parentDirectory;

    @XNode("schema")
    public String schemaName;

    @XNode("idField")
    public String idField;

    @XNode("table")
    public String tableName;

    @XNode("readOnly")
    public Boolean readOnly;

    @XNode("passwordField")
    public String passwordField;

    @XNode("passwordHashAlgorithm")
    public String passwordHashAlgorithm;

    @XNodeList(value = "permissions/permission", type = PermissionDescriptor[].class, componentType = PermissionDescriptor.class)
    public PermissionDescriptor[] permissions = null;

    @XNode("cacheTimeout")
    public Integer cacheTimeout;

    @XNode("cacheMaxSize")
    public Integer cacheMaxSize;

    @XNode("cacheEntryName")
    public String cacheEntryName = null;

    @XNode("cacheEntryWithoutReferencesName")
    public String cacheEntryWithoutReferencesName = null;

    @XNode("negativeCaching")
    public Boolean negativeCaching;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIdField(String idField) {
        this.idField = idField;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getParentDirectory() {
        return parentDirectory;
    }

    public void setParentDirectory(String parentDirectory) {
        this.parentDirectory = parentDirectory;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getPasswordField() {
        return passwordField;
    }

    public void setPasswordField(String passwordField) {
        this.passwordField = passwordField;
    }

    public String getIdField() {
        return idField;
    }

    public boolean isReadOnly() {
        return readOnly == null ? READ_ONLY_DEFAULT : readOnly.booleanValue();
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = Boolean.valueOf(readOnly);
    }

    public void setRemove(boolean delete) {
        this.remove = delete;
    }

    public boolean getRemove() {
        return this.remove;
    }

    public int getCacheTimeout() {
        return cacheTimeout == null ? CACHE_TIMEOUT_DEFAULT : cacheTimeout.intValue();
    }

    public int getCacheMaxSize() {
        return cacheMaxSize == null ? CACHE_MAX_SIZE_DEFAULT : cacheMaxSize.intValue();
    }

    /**
     * Sub-classes MUST OVERRIDE and use a more specific return type.
     * <p>
     * Usually it's bad to use clone(), and a copy-constructor is preferred, but here we want the copy method to be
     * inheritable so clone() is appropriate.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public BaseDirectoryDescriptor clone() {
        BaseDirectoryDescriptor clone;
        try {
            clone = (BaseDirectoryDescriptor) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
        // basic fields are already copied by super.clone()
        if (permissions != null) {
            clone.permissions = new PermissionDescriptor[permissions.length];
            for (int i = 0; i < permissions.length; i++) {
                clone.permissions[i] = permissions[i].clone();
            }
        }
        return clone;
    }

    public void merge(BaseDirectoryDescriptor other) {
        remove = other.remove;

        if (other.parentDirectory != null) {
            parentDirectory = other.parentDirectory;
        }
        if (other.schemaName != null) {
            schemaName = other.schemaName;
        }
        if (other.idField != null) {
            idField = other.idField;
        }
        if (other.tableName != null) {
            tableName = other.tableName;
        }
        if (other.readOnly != null) {
            readOnly = other.readOnly;
        }
        if (other.passwordField != null) {
            passwordField = other.passwordField;
        }
        if (other.passwordHashAlgorithm != null) {
            passwordHashAlgorithm = other.passwordHashAlgorithm;
        }
        if (other.permissions != null && other.permissions.length != 0) {
            permissions = other.permissions;
        }
        if (other.cacheTimeout != null) {
            cacheTimeout = other.cacheTimeout;
        }
        if (other.cacheMaxSize != null) {
            cacheMaxSize = other.cacheMaxSize;
        }
        if (other.cacheEntryName != null) {
            cacheEntryName = other.cacheEntryName;
        }
        if (other.cacheEntryWithoutReferencesName != null) {
            cacheEntryWithoutReferencesName = other.cacheEntryWithoutReferencesName;
        }
        if (other.negativeCaching != null) {
            negativeCaching = other.negativeCaching;
        }
    }

    /**
     * Creates a new {@link Directory} instance from this {@link DirectoryDescriptor).
     */
    public Directory newDirectory() {
        throw new UnsupportedOperationException();
    }

}
