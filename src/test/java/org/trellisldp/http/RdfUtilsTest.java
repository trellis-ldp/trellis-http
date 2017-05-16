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
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;

import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.Trellis;
import org.junit.Test;

/**
 * @author acoburn
 */
public class RdfUtilsTest {

    @Test
    public void testRdfInstance() {
        assertNotNull(RdfUtils.getInstance());
    }

    @Test
    public void testGetSyntax() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("text", "turtle"));

        assertEquals(of(TURTLE), RdfUtils.getRdfSyntax(types));
    }

    @Test
    public void testFilterPrefer() {
        final RDF rdf = RdfUtils.getInstance();
        final IRI iri = rdf.createIRI("trellis:repository/resource");
        final Quad q1 = rdf.createQuad(Trellis.PreferAudit, iri, DC.creator, rdf.createLiteral("me"));
        final Quad q2 = rdf.createQuad(Trellis.PreferServerManaged, iri, DC.modified, rdf.createLiteral("now"));
        final Quad q3 = rdf.createQuad(Trellis.PreferUserManaged, iri, DC.subject, rdf.createLiteral("subj"));
        final List<Quad> quads = asList(q1, q2, q3);

        final List<Quad> filtered = quads.stream().filter(RdfUtils.filterWithPrefer(
                    new Prefer("return=representation; include=\"" +
                        Trellis.PreferServerManaged.getIRIString() + "\""))).collect(toList());

        assertTrue(filtered.contains(q2));
        assertTrue(filtered.contains(q3));
        assertEquals(2, filtered.size());

        final List<Quad> filtered2 = quads.stream().filter(RdfUtils.filterWithPrefer(
                    new Prefer("return=representation"))).collect(toList());

        assertTrue(filtered2.contains(q3));
        assertEquals(1, filtered2.size());
    }

    @Test
    public void testExternalize() {
        final RDF rdf = RdfUtils.getInstance();
        final IRI iri1 = rdf.createIRI("trellis:repository/resource");
        final IRI iri2 = rdf.createIRI("http://example.org/resource");
        final Literal literal = rdf.createLiteral("Text");
        final String externalUrl = "http://localhost/api/";
        assertEquals(rdf.createIRI(externalUrl + "repository/resource"), RdfUtils.toExternalIri(iri1, externalUrl));
        assertEquals(iri2, RdfUtils.toExternalIri(iri2, externalUrl));
        assertEquals(literal, RdfUtils.toExternalIri(literal, externalUrl));
    }

}
