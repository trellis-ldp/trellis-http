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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.MAX;
import static java.time.Instant.ofEpochSecond;
import static java.time.ZonedDateTime.parse;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Arrays.asList;
import static java.util.Date.from;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.HttpHeaders.VARY;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.domain.HttpConstants.MEMENTO_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.PREFER;
import static org.trellisldp.http.domain.HttpConstants.RANGE;
import static org.trellisldp.http.domain.HttpConstants.WANT_DIGEST;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.spi.RDFUtils.getInstance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import org.trellisldp.api.Resource;
import org.trellisldp.io.JenaIOService;
import org.trellisldp.spi.AccessControlService;
import org.trellisldp.spi.AgentService;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
abstract class AbstractLdpResourceTest extends JerseyTest {

    protected final static IOService ioService = new JenaIOService(null);

    protected final static Instant time = ofEpochSecond(1496262729);

    protected final static ObjectMapper MAPPER = new ObjectMapper();

    protected final static RDF rdf = getInstance();

    protected final static IRI identifier = rdf.createIRI("trellis:repo1/resource");

    protected final static IRI agent = rdf.createIRI("user:agent");

    protected final static String BNODE_PREFIX = "trellis:bnode/";

    protected final static BlankNode bnode = rdf.createBlankNode();

    @Mock
    protected ResourceService mockResourceService;

    @Mock
    protected ConstraintService mockConstraintService;

    @Mock
    protected BinaryService mockBinaryService;

    @Mock
    protected Resource mockResource;

    @Mock
    protected Resource mockVersionedResource;

    @Mock
    protected AccessControlService mockAccessControlService;

    @Mock
    protected AgentService mockAgentService;

    @Before
    public void setUpMocks() {
        when(mockResourceService.get(any(IRI.class), any(Instant.class)))
            .thenReturn(Optional.of(mockVersionedResource));
        when(mockResourceService.get(eq(identifier))).thenReturn(Optional.of(mockResource));
        when(mockResourceService.getIdentifierSupplier()).thenReturn(() -> "randomValue");

        when(mockAgentService.asAgent(anyString())).thenReturn(agent);
        when(mockAccessControlService.anyMatch(any(Session.class), any(IRI.class), any())).thenReturn(true);
        when(mockAccessControlService.canRead(any(Session.class), any(IRI.class))).thenReturn(true);
        when(mockAccessControlService.canWrite(any(Session.class), any(IRI.class))).thenReturn(true);
        when(mockAccessControlService.canControl(any(Session.class), any(IRI.class))).thenReturn(true);

        when(mockVersionedResource.getMementos()).thenReturn(Stream.empty());
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockVersionedResource.getModified()).thenReturn(time);
        when(mockVersionedResource.getBinary()).thenReturn(Optional.empty());
        when(mockVersionedResource.isMemento()).thenReturn(true);
        when(mockVersionedResource.getIdentifier()).thenReturn(identifier);
        when(mockVersionedResource.getInbox()).thenReturn(Optional.empty());
        when(mockVersionedResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockVersionedResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockResource.getMementos()).thenReturn(Stream.empty());
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBinary()).thenReturn(Optional.empty());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getIdentifier()).thenReturn(identifier);
        when(mockResource.getInbox()).thenReturn(Optional.empty());
        when(mockResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockResourceService.unskolemize(any(IRI.class)))
            .thenAnswer(inv -> {
                final String uri = ((IRI) inv.getArgument(0)).getIRIString();
                if (uri.startsWith(BNODE_PREFIX)) {
                    return bnode;
                }
                return (IRI) inv.getArgument(0);
            });

        when(mockResourceService.unskolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.put(any(IRI.class), any(Dataset.class))).thenReturn(true);
        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class)))
            .thenAnswer(inv -> rdf.createIRI(BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));
        when(mockResource.stream()).thenReturn(Stream.of(
                rdf.createQuad(Trellis.PreferUserManaged, identifier, DC.title, rdf.createLiteral("A title")),
                rdf.createQuad(Trellis.PreferAccessControl, identifier, ACL.mode, ACL.Control)));
    }

    @Test
    public void testGetJson() throws IOException {
        final Response res = target("/repo1/resource").request().accept("application/ld+json").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertTrue(res.hasEntity());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final List<Map<String, Object>> obj = MAPPER.readValue(entity,
                new TypeReference<List<Map<String, Object>>>(){});

        assertEquals(1L, obj.size());

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> titles = (List<Map<String, String>>) obj.get(0)
                .get(DC.title.getIRIString());

        final List<String> titleVals = titles.stream().map(x -> x.get("@value")).collect(toList());

        assertEquals(1L, titleVals.size());
        assertTrue(titleVals.contains("A title"));
    }

    @Test
    public void testDefaultType() {
        final Response res = target("repo1/resource").request().get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
    }

    @Test
    public void testTrailingSlash() {
        final Response res = target("repo1/resource/").request().get();

        assertEquals(OK, res.getStatusInfo());
        assertEquals(from(time), res.getLastModified());
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("timegate") && l.getUri().toString().endsWith("repo1/resource")));
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("original") && l.getUri().toString().endsWith("repo1/resource")));
    }

    @Test
    public void testOptions1() {
        final Response res = target("repo1/resource").request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
    }

    @Test
    public void testOptions2() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        final Response res = target("repo1/resource").request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertTrue(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNotNull(res.getHeaderString(ACCEPT_POST));
        final List<String> acceptPost = asList(res.getHeaderString(ACCEPT_POST).split(","));
        assertEquals(3L, acceptPost.size());
        assertTrue(acceptPost.contains("text/turtle"));
        assertTrue(acceptPost.contains(APPLICATION_LD_JSON));
        assertTrue(acceptPost.contains(APPLICATION_N_TRIPLES));
    }

    @Test
    public void testOptions3() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));
    }

    @Test
    public void testOptions4() {
        final Response res = target("repo1/resource/").request().options();

        assertEquals(OK, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));
    }

    @Test
    public void testOptions5() {
        final Response res = target("repo1/resource").queryParam("ext", "timemap").request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));
    }

    @Test
    public void testGetJsonCompact() throws IOException {
        final Response res = target("repo1/resource").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertEquals(from(time), res.getLastModified());
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("timegate") && l.getUri().toString().endsWith("repo1/resource")));
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("original") && l.getUri().toString().endsWith("repo1/resource")));
        assertTrue(res.hasEntity());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final Map<String, Object> obj = MAPPER.readValue(entity, new TypeReference<Map<String, Object>>(){});

        assertTrue(obj.containsKey("@context"));
        assertTrue(obj.containsKey("title"));
        assertFalse(obj.containsKey("mode"));

        assertEquals("A title", (String) obj.get("title"));
    }

    @Test
    public void testGetTimeMapLink() throws IOException {
        final Response res = target("repo1/resource").queryParam("ext", "timemap").request()
            .accept(APPLICATION_LINK_FORMAT).get();

        assertEquals(OK, res.getStatusInfo());
        assertEquals(MediaType.valueOf(APPLICATION_LINK_FORMAT), res.getMediaType());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getLastModified());
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("timegate") && l.getUri().toString().endsWith("repo1/resource")));
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("original") && l.getUri().toString().endsWith("repo1/resource")));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testGetTimeMapJson() throws IOException {
        final Response res = target("repo1/resource").queryParam("ext", "timemap").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getLastModified());
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("timegate") && l.getUri().toString().endsWith("repo1/resource")));
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("original") && l.getUri().toString().endsWith("repo1/resource")));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testGetVersionJson() throws IOException {
        final Response res = target("repo1/resource").queryParam("version", 1496262729).request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertEquals(from(time), res.getLastModified());
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("timegate") && l.getUri().toString().endsWith("repo1/resource")));
        assertTrue(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("original") && l.getUri().toString().endsWith("repo1/resource")));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertEquals(time, parse(res.getHeaderString(MEMENTO_DATETIME), RFC_1123_DATE_TIME).toInstant());
    }

    @Test
    public void testGetAclJsonCompact() throws IOException {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertEquals(from(time), res.getLastModified());
        // The next two assertions may change at some point
        assertFalse(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("timegate") && l.getUri().toString().endsWith("repo1/resource")));
        assertFalse(res.getLinks().stream()
                .anyMatch(l -> l.getRel().contains("original") && l.getUri().toString().endsWith("repo1/resource")));

        assertTrue(res.hasEntity());
        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final Map<String, Object> obj = MAPPER.readValue(entity, new TypeReference<Map<String, Object>>(){});

        assertTrue(obj.containsKey("@context"));
        assertFalse(obj.containsKey("title"));
        assertTrue(obj.containsKey("mode"));
        assertEquals(ACL.Control.getIRIString(), (String) obj.get("mode"));

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testPost() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI("trellis:repo1/resource/randomValue")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target("repo1/resource").request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertEquals("http://example.org/repo1/resource/randomValue", res.getLocation().toString());
    }

    @Test
    public void testPostToLdpRs() {
        when(mockResourceService.get(eq(rdf.createIRI("trellis:repo1/resource/randomValue")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target("repo1/resource").request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPostSlug() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI("trellis:repo1/resource/test")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target("repo1/resource").request()
            .header("Slug", "test")
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertEquals("http://example.org/repo1/resource/test", res.getLocation().toString());
    }

    @Test
    public void testPostAcl() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .header("Slug", "test")
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPostInvalidContent() {
        final Response res = target("repo1/resource").request()
            .post(entity("blah blah blah", "invalid/type"));

        assertEquals(UNSUPPORTED_MEDIA_TYPE, res.getStatusInfo());
    }

    @Test
    public void testPostSlash() {
        final Response res = target("repo1/resource/").request()
            .header("Slug", "test")
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(OK, res.getStatusInfo());
    }

    @Test
    public void testPutExisting() {
        final Response res = target("repo1/resource").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
    }

    @Test
    public void testPutNew() {
        when(mockResourceService.get(eq(rdf.createIRI("trellis:repo1/resource/test")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target("repo1/resource/test").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
    }

    @Test
    public void testPutAcl() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPutInvalidContent() {
        final Response res = target("repo1/resource").request()
            .put(entity("blah blah blah", "invalid/type"));

        assertEquals(UNSUPPORTED_MEDIA_TYPE, res.getStatusInfo());
    }

    @Test
    public void testPutSlash() {
        final Response res = target("repo1/resource/").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(OK, res.getStatusInfo());
    }

    @Test
    public void testDeleteExisting() {
        final Response res = target("repo1/resource").request().delete();

        assertEquals(NO_CONTENT, res.getStatusInfo());
    }

    @Test
    public void testDeleteNonExistant() {
        when(mockResourceService.get(eq(rdf.createIRI("trellis:repo1/resource/test")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target("repo1/resource/test").request().delete();

        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testDeleteAcl() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request().delete();

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testDeleteSlash() {
        final Response res = target("repo1/resource/").request().delete();

        assertEquals(OK, res.getStatusInfo());
    }

    @Test
    public void testPatchExisting() {
        final Response res = target("repo1/resource").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
    }

    @Test
    public void testPatchNew() {
        when(mockResourceService.get(eq(rdf.createIRI("trellis:repo1/resource/test")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target("repo1/resource/test").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testPatchAcl() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
    }

    @Test
    public void testPatchInvalidContent() {
        final Response res = target("repo1/resource").request()
            .method("PATCH", entity("blah blah blah", "invalid/type"));

        assertEquals(UNSUPPORTED_MEDIA_TYPE, res.getStatusInfo());
    }

    @Test
    public void testPatchSlash() {
        final Response res = target("repo1/resource/").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(OK, res.getStatusInfo());
    }


    protected static Predicate<Link> hasLink(final IRI iri, final String rel) {
        return link -> rel.equals(link.getRel()) && iri.getIRIString().equals(link.getUri().toString());
    }

    protected static Predicate<Link> hasType(final IRI iri) {
        return hasLink(iri, "type");
    }
}
