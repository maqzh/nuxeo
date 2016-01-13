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
package org.nuxeo.ecm.core.storage.mongodb;

import org.nuxeo.ecm.core.repository.RepositoryFactory;
import org.nuxeo.ecm.core.storage.dbs.DBSRepositoryFactory;

/**
 * MongoDB implementation of a {@link RepositoryFactory}, creating a {@link MongoDBRepository}.
 *
 * @since 5.9.4
 */
public class MongoDBRepositoryFactory extends DBSRepositoryFactory {

    public MongoDBRepositoryFactory(String repositoryName) {
        super(repositoryName);
    }

    @Override
    public Object call() {
        return new MongoDBRepository((MongoDBRepositoryDescriptor) getRepositoryDescriptor());
    }

}
