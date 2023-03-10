/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.security.authservice.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * A backport of AuthenticationConfiguration#getAuthenticationService.
 * 
 * @version $Id$
 */
@Component(roles = AuthenticationServiceConfiguration.class)
@Singleton
public class AuthenticationServiceConfiguration
{
    /**
     * The spaces in which the authentication configuration is stored.
     */
    public static final List<String> SPACE_NAMES = Arrays.asList("XWiki", "Authentication");

    /**
     * The reference of the class holding the configuration of the authentication.
     */
    public static final LocalDocumentReference CLASS_REFERENCE =
        new LocalDocumentReference(SPACE_NAMES, "ConfigurationClass");

    /**
     * The serialized reference of the class holding the configuration of the authentication.
     */
    public static final String CLASS_REFERENCE_STRING = "XWiki.Authentication.ConfigurationClass";

    /**
     * The reference of the document holding the configuration of the authentication.
     */
    public static final LocalDocumentReference DOC_REFERENCE = new LocalDocumentReference(SPACE_NAMES, "Configuration");

    /**
     * The name of the property containing the identifier of the authenticator in the wiki.
     */
    public static final String CONFIGURATION_WIKI_PROPERTY = "authService";

    /**
     * The name of the property containing the identifier of the authenticator in the xwiki.properties file.
     */
    public static final String CONFIGURATION_INSTANCE_PROPERTY =
        "security.authentication." + CONFIGURATION_WIKI_PROPERTY;

    private class ServiceCacheEntry
    {
        private final String name;

        ServiceCacheEntry(String name)
        {
            this.name = name;
        }
    }

    private Map<String, ServiceCacheEntry> serviceCache = new ConcurrentHashMap<>();

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("xwikiproperties")
    private ConfigurationSource configurationSource;

    /**
     * @return the hint of the configured authentication service
     * @throws XWikiException when failing to load the configuration
     */
    public String getAuthenticationService() throws XWikiException
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        // TODO: Try at current wiki level

        // Try at main wiki level
        String service = getAuthenticationService(new WikiReference(xcontext.getMainXWiki()), xcontext);
        if (service != null) {
            return service;
        }

        // Try at xwiki.properties level
        return this.configurationSource.getProperty(CONFIGURATION_INSTANCE_PROPERTY);
    }

    private String getAuthenticationService(WikiReference wiki, XWikiContext xcontext) throws XWikiException
    {
        ServiceCacheEntry service = this.serviceCache.get(wiki.getName());

        if (service == null) {
            service = new ServiceCacheEntry(loadAuthenticationService(wiki, xcontext));

            this.serviceCache.put(wiki.getName(), service);
        }

        return service.name;
    }

    private String loadAuthenticationService(WikiReference wiki, XWikiContext xcontext) throws XWikiException
    {
        XWikiDocument configurationDocument =
            xcontext.getWiki().getDocument(new DocumentReference(DOC_REFERENCE, wiki), xcontext);
        BaseObject configurationObject = configurationDocument.getXObject(CLASS_REFERENCE);

        String serviceName = configurationObject.getStringValue(CONFIGURATION_WIKI_PROPERTY);

        return StringUtils.isBlank(serviceName) ? null : serviceName;
    }

    private void setAuthenticationService(String id, WikiReference wiki, XWikiContext xcontext) throws XWikiException
    {
        XWikiDocument configurationDocument =
            xcontext.getWiki().getDocument(new DocumentReference(DOC_REFERENCE, wiki), xcontext);
        BaseObject configurationObject = configurationDocument.getXObject(CLASS_REFERENCE);

        configurationObject.setStringValue(CONFIGURATION_WIKI_PROPERTY, StringUtils.defaultString(id));

        xcontext.getWiki().saveDocument(configurationDocument, "Change authenticator service", xcontext);
    }

    /**
     * @param id the identifier of the authenticator
     * @throws XWikiException when failing to update the configuration
     */
    public void setAuthenticationService(String id) throws XWikiException
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        setAuthenticationService(id, new WikiReference(xcontext.getMainXWiki()), xcontext);
    }

    /**
     * @param wikiId the identifier of the wiki to remove from the cache
     */
    public void invalidate(String wikiId)
    {
        this.serviceCache.remove(wikiId);
    }
}
