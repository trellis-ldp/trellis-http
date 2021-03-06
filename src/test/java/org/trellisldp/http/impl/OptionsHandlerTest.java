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
package org.trellisldp.http.impl;

import static java.time.Instant.ofEpochSecond;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.trellisldp.api.RDFUtils.getInstance;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.PATCH;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE;

import java.time.Instant;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@RunWith(JUnitPlatform.class)
public class OptionsHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static Instant binaryTime = ofEpochSecond(1496262750);
    private final static String baseUrl = "http://localhost:8080/repo";
    private final static RDF rdf = getInstance();

    private Binary testBinary = new Binary(rdf.createIRI("file:testResource.txt"), binaryTime, "text/plain", 100L);

    @Mock
    private ResourceService mockResourceService;

    @Mock
    private Resource mockResource;

    @Mock
    private LdpRequest mockRequest;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        when(mockResource.getMementos()).thenReturn(emptyList());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getTypes()).thenReturn(emptyList());
        when(mockRequest.getBaseUrl()).thenReturn(baseUrl);
        when(mockRequest.getPath()).thenReturn("/");
    }

    @Test
    public void testOptionsLdprs() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        final OptionsHandler optionsHandler = new OptionsHandler(mockRequest, mockResourceService, null);

        final Response res = optionsHandler.ldpOptions(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertFalse(allow.contains(POST));
    }

    @Test
    public void testOptionsLdpc() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.IndirectContainer);
        final OptionsHandler optionsHandler = new OptionsHandler(mockRequest, mockResourceService, baseUrl);

        final Response res = optionsHandler.ldpOptions(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());

        final String acceptPost = res.getHeaderString(ACCEPT_POST);
        assertNotNull(acceptPost);
        assertTrue(acceptPost.contains(APPLICATION_LD_JSON));
        assertTrue(acceptPost.contains(APPLICATION_N_TRIPLES));
        assertTrue(acceptPost.contains(TEXT_TURTLE.split(";")[0]));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));

        final String allow = res.getHeaderString(ALLOW);
        assertNotNull(allow);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertTrue(allow.contains(POST));
    }

    @Test
    public void testOptionsLdpnr() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);

        final OptionsHandler optionsHandler = new OptionsHandler(mockRequest, mockResourceService, null);

        final Response res = optionsHandler.ldpOptions(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertFalse(allow.contains(POST));
    }

    @Test
    public void testOptionsAcl() {
        when(mockRequest.getExt()).thenReturn("acl");

        final OptionsHandler optionsHandler = new OptionsHandler(mockRequest, mockResourceService, baseUrl);

        final Response res = optionsHandler.ldpOptions(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertNull(res.getHeaderString(ACCEPT_POST));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertFalse(allow.contains(POST));
    }

    @Test
    public void testOptionsMemento() {
        when(mockResource.isMemento()).thenReturn(true);

        final OptionsHandler optionsHandler = new OptionsHandler(mockRequest, mockResourceService, null);

        final Response res = optionsHandler.ldpOptions(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertFalse(allow.contains(PUT));
        assertFalse(allow.contains(DELETE));
        assertFalse(allow.contains(PATCH));
        assertFalse(allow.contains(POST));
    }

    @Test
    public void testOptionsDeleted() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Resource);
        when(mockResource.getTypes()).thenReturn(asList(Trellis.DeletedResource));

        final OptionsHandler optionsHandler = new OptionsHandler(mockRequest, mockResourceService, baseUrl);

        assertThrows(WebApplicationException.class, () -> optionsHandler.ldpOptions(mockResource));
    }
}
