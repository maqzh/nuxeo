/*
 * (C) Copyright 2006-2008 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Alexandre Russel
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.annotations.repository;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.annotations.api.UriResolver;
import org.nuxeo.ecm.platform.url.api.DocumentView;
import org.nuxeo.ecm.platform.url.api.DocumentViewCodecManager;
import org.nuxeo.ecm.platform.web.common.vh.VirtualHostHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * @author Alexandre Russel
 */
public class DefaultNuxeoUriResolver implements UriResolver {

    private static final String NUXEO = VirtualHostHelper.getContextPathProperty() + "/";

    private static final Log log = LogFactory.getLog(DefaultNuxeoUriResolver.class);

    private final URNDocumentViewTranslator translator = new URNDocumentViewTranslator();

    private DocumentViewCodecManager viewCodecManager;

    public DefaultNuxeoUriResolver() {
        viewCodecManager = Framework.getService(DocumentViewCodecManager.class);
    }

    public URI getSearchURI(URI uri) {
        DocumentView view = translator.getDocumentViewFromUri(uri);
        URI translatedUri = null;
        try (CoreSession session = CoreInstance.openCoreSession(null)) {
            DocumentRef idRef = view.getDocumentLocation().getIdRef();
            if (idRef == null) {
                DocumentModel docModel = session.getDocument(view.getDocumentLocation().getDocRef());
                idRef = docModel.getRef();
            }
            translatedUri = translator.getUriFromDocumentView(view.getDocumentLocation().getServerName(), idRef);
        }
        return translatedUri;
    }

    public URI translateFromGraphURI(URI uri, String baseUrl) {
        DocumentView view = translator.getDocumentViewFromUri(uri);
        if (view == null || baseUrl == null) { // not a nuxeo document or
            // already a urn
            return uri;
        }
        String url = viewCodecManager.getUrlFromDocumentView(view, true, baseUrl);
        URI u = null;
        try {
            u = new URI(url);
        } catch (URISyntaxException e) {
            throw new NuxeoException(e);
        }
        return u;
    }

    public URI translateToGraphURI(URI uri) {
        if (uri.toString().startsWith("urn")) {
            return uri;
        }
        DocumentView view = viewCodecManager.getDocumentViewFromUrl(uri.toString(), true, getBaseUrl(uri));
        if (view == null) {// not a nuxeo uri
            return uri;
        }
        URI result;
        try (CoreSession session = CoreInstance.openCoreSession(null)) {
            DocumentRef idRef = view.getDocumentLocation().getIdRef();
            if (idRef == null) {
                DocumentModel docModel = session.getDocument(view.getDocumentLocation().getDocRef());
                idRef = docModel.getRef();
            }
            result = translator.getUriFromDocumentView(view.getDocumentLocation().getServerName(), idRef);
        }
        return result;
    }

    public String getBaseUrl(URI uri) {
        String url;
        try {
            url = uri.toURL().toString();
        } catch (MalformedURLException e) {
            throw new NuxeoException(e);
        }
        return url.substring(0, url.lastIndexOf(NUXEO) + NUXEO.length());
    }

    public DocumentRef getDocumentRef(URI uri) {
        DocumentView view = null;
        if (translator.isNuxeoUrn(uri)) {
            view = translator.getDocumentViewFromUri(uri);
        } else {
            view = viewCodecManager.getDocumentViewFromUrl(uri.toString(), true, getBaseUrl(uri));
            if (view == null) {
                return null;
            }
        }
        DocumentLocation location = view.getDocumentLocation();
        return location.getDocRef();
    }

    public DocumentLocation getDocumentLocation(URI uri) {
        DocumentView view;
        if (translator.isNuxeoUrn(uri)) {
            view = translator.getDocumentViewFromUri(uri);
        } else {
            view = viewCodecManager.getDocumentViewFromUrl(uri.toString(), true, getBaseUrl(uri));
            if (view == null) {
                return null;
            }
        }
        return view.getDocumentLocation();
    }

    public URI getUri(DocumentView view, String baseUrl) throws URISyntaxException {
        return new URI(viewCodecManager.getUrlFromDocumentView(view, true, baseUrl));
    }

}
