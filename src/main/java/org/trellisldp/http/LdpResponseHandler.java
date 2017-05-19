/*
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
 */
package org.trellisldp.http;

import static org.trellisldp.http.RdfUtils.getInstance;

import java.io.InputStream;
import java.time.Instant;
import java.util.function.BiFunction;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;

/**
 * The base response builder class
 *
 * @author acoburn
 */
abstract class LdpResponseHandler {

    protected static final RDF rdf = getInstance();

    protected final ResourceService resourceService;

    protected String baseUrl = "";
    protected String update = null;
    protected InputStream entity = null;
    protected IRI profile = null;
    protected Prefer prefer = null;
    protected Boolean timemap = false;
    protected Version version = null;
    protected RDFSyntax syntax = null;
    protected AcceptDatetime datetime = null;
    protected WantDigest digest = null;
    protected Range range = null;
    protected BiFunction<Instant, EntityTag, ResponseBuilder> evaluator = null;
    protected Session session = null;
    protected String contentType = null;
    protected String slug = null;
    protected Link link = null;

    /**
     * A LDP Response builder
     * @param resourceService the resourceService
     */
    protected LdpResponseHandler(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Add a base URL to the response
     * @param url the URL
     * @return the Response builder
     */
    public LdpResponseHandler withBaseUrl(final String url) {
        this.baseUrl = url;
        return this;
    }

    /**
     * Add a cache evaluator
     * @param evaluator the evaluator
     * @return the Response builder
     */
    public LdpResponseHandler withCacheEvaluator(final BiFunction<Instant, EntityTag, ResponseBuilder> evaluator) {
        this.evaluator = evaluator;
        return this;
    }

    /**
     * Add a prefer object
     * @param prefer the prefer object
     * @return the Response builder
     */
    public LdpResponseHandler withPrefer(final Prefer prefer) {
        this.prefer = prefer;
        return this;
    }

    /**
     * Add a profile object
     * @param profile the profile object
     * @return the Response builder
     */
    public LdpResponseHandler withProfile(final IRI profile) {
        this.profile = profile;
        return this;
    }

    /**
     * Add a version object
     * @param version the version object
     * @return the Response builder
     */
    public LdpResponseHandler withVersion(final Version version) {
        this.version = version;
        return this;
    }

    /**
     * Add an RDFSyntax object
     * @param syntax the syntax object
     * @return the Response builder
     */
    public LdpResponseHandler withSyntax(final RDFSyntax syntax) {
        this.syntax = syntax;
        return this;
    }

    /**
     * Add a timemap marker
     * @param timemap whether to include a timemap
     * @return the Response builder
     */
    public LdpResponseHandler withTimemap(final Boolean timemap) {
        this.timemap = timemap;
        return this;
    }

    /**
     * Add a datetime object
     * @param datetime the datetime object
     * @return the Response builder
     */
    public LdpResponseHandler withDatetime(final AcceptDatetime datetime) {
        this.datetime = datetime;
        return this;
    }

    /**
     * Add a want-digest object
     * @param digest the want-digest object
     * @return the Response builder
     */
    public LdpResponseHandler withWantDigest(final WantDigest digest) {
        this.digest = digest;
        return this;
    }

    /**
     * Add a range abject
     * @param range the range object
     * @return the Response builder
     */
    public LdpResponseHandler withRange(final Range range) {
        this.range = range;
        return this;
    }

    /**
     * Add a session
     * @param session the session
     * @return te Response builder
     */
    public LdpResponseHandler withSession(final Session session) {
        this.session = session;
        return this;
    }

    /**
     * Add a Sparql-Update command
     * @param update the sparql update command
     * @return the Response builder
     */
    public LdpResponseHandler withSparqlUpdate(final String update) {
        this.update = update;
        return this;
    }

    /**
     * Add an entity
     * @param entity the entity
     * @return the Response builder
     */
    public LdpResponseHandler withEntity(final InputStream entity) {
        this.entity = entity;
        return this;
    }

    /**
     * Add a slug
     * @param slug the slug
     * @return the Response builder
     */
    public LdpResponseHandler withSlug(final String slug) {
        this.slug = slug;
        return this;
    }

    /**
     * Add an interaction model
     * @param model the model
     * @return the Response builder
     */
    public LdpResponseHandler withLink(final Link link) {
        this.link = link;
        return this;
    }

    /**
     * Add a content-type
     * @param contentType the content type
     * @return the Response builder
     */
    public LdpResponseHandler withContentType(final String contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * Build the response
     * @param path the path
     * @return the response
     */
    public abstract Response build(final String path);
}
