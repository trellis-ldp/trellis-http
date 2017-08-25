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

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.codec.digest.DigestUtils.getDigest;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.codec.digest.DigestUtils.updateDigest;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.http.impl.RdfUtils.skolemizeTriples;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;
import static org.trellisldp.spi.RDFUtils.auditUpdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;

import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.Trellis;
import org.trellisldp.vocabulary.XSD;

/**
 * The PUT response handler
 *
 * @author acoburn
 */
public class PutHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(PutHandler.class);

    private final BinaryService binaryService;
    private final ConstraintService constraintService;
    private final IOService ioService;
    private final File entity;

    /**
     * Create a builder for an LDP POST response
     * @param partitions the partitions
     * @param req the LDP request
     * @param entity the entity
     * @param resourceService the resource service
     * @param ioService the serialization service
     * @param constraintService the RDF constraint service
     * @param binaryService the binary service
     */
    public PutHandler(final Map<String, String> partitions, final LdpRequest req, final File entity,
            final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService, final BinaryService binaryService) {
        super(partitions, req, resourceService);
        this.ioService = ioService;
        this.constraintService = constraintService;
        this.binaryService = binaryService;
        this.entity = entity;
    }

    /**
     * Set the data for a resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder setResource(final Resource res) {
        final String identifier = req.getBaseUrl(partitions) + req.getPartition() + req.getPath();
        final EntityTag etag;
        final Instant modified;

        if (res.getBinary().isPresent() && !ofNullable(req.getContentType()).flatMap(RDFSyntax::byMediaType)
                .isPresent()) {
            modified = res.getBinary().map(Binary::getModified).get();
            etag = new EntityTag(md5Hex(modified + identifier));
        } else {
            modified = res.getModified();
            etag = new EntityTag(md5Hex(modified + identifier), true);
        }

        // Check the cache
        final ResponseBuilder cache = checkCache(req.getRequest(), modified, etag);
        if (nonNull(cache)) {
            return cache;
        }
        return setResource();
    }

    /**
     * Set the data for a resource
     * @return the response builder
     */
    public ResponseBuilder setResource() {
        final String baseUrl = req.getBaseUrl(partitions);
        final String identifier = baseUrl + req.getPartition() + req.getPath();
        final String contentType = req.getContentType();
        final Session session = ofNullable(req.getSession()).orElse(new HttpSession());
        final Optional<RDFSyntax> rdfSyntax = ofNullable(contentType).flatMap(RDFSyntax::byMediaType)
            .filter(SUPPORTED_RDF_TYPES::contains);

        LOGGER.info("Setting resource as {}", identifier);

        final IRI defaultType = nonNull(contentType) && !rdfSyntax.isPresent() ? LDP.NonRDFSource : LDP.RDFSource;
        final IRI ldpType = ofNullable(req.getLink()).filter(l -> "type".equals(l.getRel()))
                    .map(Link::getUri).map(URI::toString).map(rdf::createIRI)
                    .filter(l -> !LDP.Resource.equals(l)).orElse(defaultType);

        final IRI internalId = rdf.createIRI(TRELLIS_PREFIX + req.getPartition() + req.getPath());

        final Dataset dataset = rdf.createDataset();
        final IRI graphName = ACL.equals(req.getExt()) ? Trellis.PreferAccessControl : Trellis.PreferUserManaged;

        // Add audit quads
        auditUpdate(internalId, session).stream().map(skolemizeQuads(resourceService, baseUrl))
            .forEach(dataset::add);

        // Add LDP type
        dataset.add(rdf.createQuad(Trellis.PreferServerManaged, internalId, RDF.type, ldpType));

        // Add user-supplied data
        try {
            if (nonNull(entity) && rdfSyntax.isPresent()) {
                ioService.read(new FileInputStream(entity), identifier, rdfSyntax.get())
                    .map(skolemizeTriples(resourceService, baseUrl)).forEach(triple -> {
                        dataset.add(rdf.createQuad(graphName, triple.getSubject(),
                                triple.getPredicate(), triple.getObject()));
                    });

                // Check for any constraints
                final Optional<String> constraint = dataset.getGraph(graphName)
                    .flatMap(g -> constraintService.constrainedBy(ldpType, baseUrl, g)).map(IRI::getIRIString);
                if (constraint.isPresent()) {
                    return status(BAD_REQUEST).link(constraint.get(), LDP.constrainedBy.getIRIString());
                }
            } else if (nonNull(entity)) {
                if (nonNull(req.getDigest())) {
                    // Check the expected digest value
                    final String digest = encodeBase64String(updateDigest(getDigest(req.getDigest().getAlgorithm()),
                                new FileInputStream(entity)).digest());
                    if (!digest.equals(req.getDigest().getDigest())) {
                        return status(BAD_REQUEST);
                    }
                }

                // TODO JDK9, use map literal
                final Map<String, String> metadata = new HashMap<>();
                metadata.put(CONTENT_TYPE, ofNullable(contentType).orElse(APPLICATION_OCTET_STREAM));
                final IRI binaryLocation = rdf.createIRI(binaryService.getIdentifierSupplier(req.getPartition()).get());
                binaryService.setContent(req.getPartition(), binaryLocation, new FileInputStream(entity));
                dataset.add(rdf.createQuad(Trellis.PreferServerManaged, internalId, DC.hasPart, binaryLocation));
                dataset.add(rdf.createQuad(Trellis.PreferServerManaged, binaryLocation, DC.format,
                            rdf.createLiteral(ofNullable(req.getContentType()).orElse(APPLICATION_OCTET_STREAM))));
                dataset.add(rdf.createQuad(Trellis.PreferServerManaged, binaryLocation, DC.extent,
                            rdf.createLiteral(Long.toString(entity.length()), XSD.long_)));
            }
        } catch (final IOException ex) {
            throw new WebApplicationException(ex);
        }

        if (resourceService.put(internalId, dataset)) {
            final ResponseBuilder builder = status(NO_CONTENT);

            ldpResourceTypes(ldpType).map(IRI::getIRIString)
                .forEach(type -> builder.link(type, "type"));
            return builder;
        }

        LOGGER.error("Unable to persist data to location at {}", internalId.getIRIString());
        return serverError().type(TEXT_PLAIN)
            .entity("Unable to persist data. Please consult the logs for more information");
    }
}