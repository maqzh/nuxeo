/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.ecm.restapi.jaxrs.io.usermanager;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.impl.NuxeoGroupImpl;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.usermanager.io.NuxeoGroupJsonReader;
import org.nuxeo.ecm.webengine.WebException;
import org.nuxeo.ecm.webengine.jaxrs.coreiodelegate.JsonCoreIODelegate;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 5.7.3
 * @deprecated since 7.10 The Nuxeo JSON marshalling was migrated to nuxeo-core-io. This class is replaced by
 *             {@link NuxeoGroupJsonReader} which is registered by default and available to marshal {@link NuxeoGroup}
 *             from the Nuxeo Rest API thanks to the JAX-RS marshaller {@link JsonCoreIODelegate}
 */
@Deprecated
public class NuxeoGroupReader implements MessageBodyReader<NuxeoGroup> {

    @Context
    JsonFactory factory;

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return NuxeoGroup.class.isAssignableFrom(type);
    }

    @Override
    public NuxeoGroup readFrom(Class<NuxeoGroup> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException,
            WebApplicationException {
        String content = IOUtils.toString(entityStream);
        if (content.isEmpty()) {
            throw new WebException("No content in request body", Response.Status.BAD_REQUEST.getStatusCode());
        }

        return readRequest(content, httpHeaders);

    }

    /**
     * @param content
     * @param httpHeaders
     * @return
     */
    private NuxeoGroup readRequest(String json, MultivaluedMap<String, String> httpHeaders) {
        try {
            JsonParser jp = factory.createJsonParser(json);
            return readJson(jp, httpHeaders);
        } catch (NuxeoException | IOException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * @param jp
     * @param httpHeaders
     * @return
     * @throws IOException
     * @throws JsonParseException
     */
    private NuxeoGroup readJson(JsonParser jp, MultivaluedMap<String, String> httpHeaders) throws JsonParseException,
            IOException {
        JsonToken tok = jp.nextToken();

        // skip {
        if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
            tok = jp.nextToken();
        }
        String id = null;

        UserManager um = Framework.getLocalService(UserManager.class);
        NuxeoGroup group = null;

        while (tok != JsonToken.END_OBJECT) {
            String key = jp.getCurrentName();
            jp.nextToken();
            if ("groupname".equals(key)) {
                id = jp.readValueAs(String.class);

                group = um.getGroup(id);
                if (group == null) {
                    group = new NuxeoGroupImpl(id);
                }
            } else if ("grouplabel".equals(key)) {
                group.setLabel(jp.readValueAs(String.class));
            } else if ("memberUsers".equals(key)) {
                tok = jp.nextToken();
                List<String> users = new ArrayList<>();
                while (tok != JsonToken.END_ARRAY) {
                    users.add(jp.readValueAs(String.class));
                    tok = jp.nextToken();
                }
                group.setMemberUsers(users);
            } else if ("memberGroups".equals(key)) {
                tok = jp.nextToken();
                List<String> groups = new ArrayList<>();
                while (tok != JsonToken.END_ARRAY) {
                    groups.add(jp.readValueAs(String.class));
                    tok = jp.nextToken();
                }
                group.setMemberGroups(groups);
            } else if ("entity-type".equals(key)) {
                String entityType = jp.readValueAs(String.class);
                if (!NuxeoGroupWriter.ENTITY_TYPE.equals(entityType)) {
                    throw new WebApplicationException(Response.Status.BAD_REQUEST);
                }
            }
            tok = jp.nextToken();
        }
        return group;

    }

}
