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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.nonNull;
import static javax.ws.rs.Priorities.AUTHORIZATION;
import static javax.ws.rs.core.HttpHeaders.LINK;
import static javax.ws.rs.core.Link.fromUri;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.SecurityContext.BASIC_AUTH;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.SESSION_PROPERTY;
import static org.trellisldp.api.RDFUtils.TRELLIS_PREFIX;
import static org.trellisldp.api.RDFUtils.getInstance;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Priority;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.slf4j.Logger;
import org.trellisldp.api.AccessControlService;
import org.trellisldp.api.Session;
import org.trellisldp.http.domain.HttpConstants;
import org.trellisldp.http.impl.HttpSession;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@PreMatching
@Priority(AUTHORIZATION - 100)
public class WebAcFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final RDF rdf = getInstance();

    private static final Logger LOGGER = getLogger(WebAcFilter.class);

    private final AccessControlService accessService;
    private final Map<String, String> partitions;
    private final List<String> challenges;
    private static final Set<String> readable = new HashSet<>(asList("GET", "HEAD", "OPTIONS"));
    private static final Set<String> writable = new HashSet<>(asList("PUT", "PATCH", "DELETE"));
    private static final Set<String> appendable = new HashSet<>(asList("POST"));

    /**
     * Create a new WebAc-based auth filter
     * @param partitions the partitions in use
     * @param challenges the challenges
     * @param accessService the access service
     */
    public WebAcFilter(final Map<String, String> partitions, final List<String> challenges,
            final AccessControlService accessService) {
        this.accessService = accessService;
        this.partitions = partitions;
        this.challenges = challenges.isEmpty() ? singletonList(BASIC_AUTH) : challenges;
    }

    @Override
    public void filter(final ContainerRequestContext ctx) throws IOException {
        final String path = ctx.getUriInfo().getPath();
        final Object session = ctx.getProperty(SESSION_PROPERTY);
        final Session s;
        if (nonNull(session)) {
            s = (Session) session;
        } else {
            s = new HttpSession();
            ctx.setProperty(SESSION_PROPERTY, s);
        }
        final String method = ctx.getMethod();
        final String partition = path.split("/")[0];

        if (partitions.containsKey(partition)) {
            final Set<IRI> modes = accessService.getAccessModes(rdf.createIRI(TRELLIS_PREFIX + path), s);
            if (ctx.getUriInfo().getQueryParameters().getOrDefault("ext", emptyList()).contains(HttpConstants.ACL)) {
                verifyCanControl(modes, s, path);
            } else if (readable.contains(method)) {
                verifyCanRead(modes, s, path);
            } else if (writable.contains(method)) {
                verifyCanWrite(modes, s, path);
            } else if (appendable.contains(method)) {
                verifyCanAppend(modes, s, path);
            } else {
                throw new NotAllowedException(status(METHOD_NOT_ALLOWED).build());
            }
        }
    }

    @Override
    public void filter(final ContainerRequestContext req, final ContainerResponseContext res) throws IOException {
        if (!req.getUriInfo().getQueryParameters().containsKey("ext") ||
                !req.getUriInfo().getQueryParameters().get("ext").contains("acl")) {
            res.getHeaders().add(LINK, fromUri(req.getUriInfo().getAbsolutePathBuilder()
                    .queryParam("ext", "acl").build()).rel(HttpConstants.ACL).build());
        }
    }

    private void verifyCanAppend(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Append) && !modes.contains(ACL.Write)) {
            LOGGER.warn("User: {} cannot Append to {}", session.getAgent(), path);
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException(challenges.get(0),
                        challenges.subList(1, challenges.size()).toArray());
            }
            throw new ForbiddenException();
        }
    }

    private void verifyCanControl(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Control)) {
            LOGGER.warn("User: {} cannot Control {}", session.getAgent(), path);
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException(challenges.get(0),
                        challenges.subList(1, challenges.size()).toArray());
            }
            throw new ForbiddenException();
        }
    }

    private void verifyCanWrite(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Write)) {
            LOGGER.warn("User: {} cannot Write to {}", session.getAgent(), path);
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException(challenges.get(0),
                        challenges.subList(1, challenges.size()).toArray());
            }
            throw new ForbiddenException();
        }
    }

    private void verifyCanRead(final Set<IRI> modes, final Session session, final String path) {
        if (!modes.contains(ACL.Read)) {
            LOGGER.warn("User: {} cannot Read from {}", session.getAgent(), path);
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException(challenges.get(0),
                        challenges.subList(1, challenges.size()).toArray());
            }
            throw new ForbiddenException();
        }
    }
}
