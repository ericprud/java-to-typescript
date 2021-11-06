package com.janeirodigital.shapetrees.core.methodhandlers;

import com.janeirodigital.shapetrees.core.*;
import com.janeirodigital.shapetrees.core.enums.HttpHeaders;
import com.janeirodigital.shapetrees.core.enums.LinkRelations;
import com.janeirodigital.shapetrees.core.enums.ShapeTreeResourceType;
import com.janeirodigital.shapetrees.core.exceptions.ShapeTreeException;
import com.janeirodigital.shapetrees.core.helpers.GraphHelper;
import com.janeirodigital.shapetrees.core.models.*;
import com.janeirodigital.shapetrees.core.vocabularies.LdpVocabulary;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.*;
import static com.janeirodigital.shapetrees.core.helpers.GraphHelper.urlToUri;
/**
 * Abstract class providing reusable functionality to different method handlers
 */
@Slf4j
public abstract class AbstractValidatingMethodHandler {
    public static final String TEXT_TURTLE = "text/turtle";
    private static final String POST = "POST";
    private static final String PUT = "PUT";
    private static final String PATCH = "PATCH";
    private static final String DELETE = "DELETE";
    protected final ResourceAccessor resourceAccessor;
    protected final Set<String> supportedRDFContentTypes = Set.of(TEXT_TURTLE, "application/rdf+xml", "application/n-triples", "application/ld+json");
    protected final Set<String> supportedSPARQLContentTypes = Set.of("application/sparql-update");

    protected AbstractValidatingMethodHandler(final ResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    protected DocumentResponse manageShapeTree(@Nullable final ShapeTreeResource primaryResource, @NotNull final ShapeTreeRequest shapeTreeRequest) throws ShapeTreeException {

        Optional<DocumentResponse> validationResponse = null;
        final ShapeTreeLocator updatedRootLocator = this.getShapeTreeLocatorFromRequest(shapeTreeRequest, primaryResource.getMetadataResourceFork());
        final ShapeTreeLocator existingRootLocator = this.getShapeTreeLocatorFromResource(primaryResource.getMetadataResourceFork());

        // Determine ShapeTreeLocations that have been removed, added, and/or updated
        final ShapeTreeLocatorDelta delta = ShapeTreeLocatorDelta.evaluate(existingRootLocator, updatedRootLocator);

        // It is invalid for a locator resource to be left with no locations.
        // Shape Trees, §3: A shape tree locator includes one or more shape tree locations via st:location.
        if (delta.allRemoved()) {
            this.ensureAllRemovedFromLocatorByDelete(shapeTreeRequest); }

        if (delta.wasReduced()) {
            // An existing location has been removed from the locator for the primary resource.
            validationResponse = this.unplantShapeTree(primaryResource, primaryResource.getShapeTreeContext(), delta);
            if (validationResponse.isPresent()) { return validationResponse.get(); }
        }

        if (delta.isUpdated()) {
            // An existing location has been updated, or new locations have been added
            validationResponse = this.plantShapeTree(primaryResource, primaryResource.getShapeTreeContext(), updatedRootLocator, delta);
            if (validationResponse.isPresent()) { return validationResponse.get(); }
        }

        // TODO: Test: Need a test with reduce and updated delta to make sure we never return success from plant or unplant.

        return this.successfulValidation();
    }

    /**
     * Plants a shape tree on an existing resource
     * @param primaryResource
     * @param shapeTreeContext
     * @param updatedRootLocator
     * @param delta
     * @return DocumentResponse
     * @throws IOException
     * @throws MalformedURLException
     */
    protected Optional<DocumentResponse> plantShapeTree(final ShapeTreeResource primaryResource, final ShapeTreeContext shapeTreeContext, final ShapeTreeLocator updatedRootLocator, final ShapeTreeLocatorDelta delta) throws ShapeTreeException {

        // Cannot directly update locations that are not root locations
        this.ensureUpdatedLocationsAreRootLocations(delta);

        // Run recursive assignment for each updated location in the root locator
        for (final ShapeTreeLocation rootLocation : delta.getUpdatedLocations()) {
            final Optional<DocumentResponse> validationResponse = this.assignShapeTreeToResource(primaryResource, shapeTreeContext, updatedRootLocator, rootLocation, rootLocation, null);
            if (validationResponse.isPresent()) { return validationResponse; }
        }

        return Optional.empty();
    }

    protected  Optional<DocumentResponse> unplantShapeTree(final ShapeTreeResource primaryResource, final ShapeTreeContext shapeTreeContext, final ShapeTreeLocatorDelta delta) throws ShapeTreeException {

        this.ensureRemovedLocationsAreRootLocations(delta); // Cannot unplant a non-root location

        // Run recursive unassignment for each removed location in the updated root locator
        for (final ShapeTreeLocation rootLocation : delta.getRemovedLocations()) {
            final Optional<DocumentResponse> validationResponse = this.unassignShapeTreeFromResource(primaryResource, shapeTreeContext, rootLocation);
            if (validationResponse.isPresent()) { return validationResponse; }
        }

        return Optional.empty();
    }

    // TODO: #87: do sanity checks on meta of meta, c.f. @see https://github.com/xformativ/shapetrees-java/issues/87
    protected Optional<DocumentResponse> createShapeTreeInstance(final ShapeTreeResource targetResource, final ShapeTreeResource containerResource, final ShapeTreeRequest shapeTreeRequest, final String proposedName) throws ShapeTreeException {
        // Sanity check user-owned resource @@ delete 'cause type checks
        this.ensureShapeTreeResourceExists(containerResource.getPrimaryResourceFork(),"Target container for resource creation not found");
        this.ensureRequestResourceIsContainer(containerResource.getPrimaryResourceFork(),"Cannot create a shape tree instance in a non-container resource");

        // Prepare the target resource for validation and creation
        final URL targetResourceUrl = this.normalizeSolidResourceUrl(containerResource.getPrimaryResourceFork().getUrl(), proposedName, shapeTreeRequest.getResourceType());
        this.ensureTargetPrimaryResourceDoesNotExist(targetResource.getShapeTreeContext(), targetResourceUrl,"Cannot create a shape tree instance in a non-container resource " + targetResourceUrl);

        this.ensureShapeTreeResourceExists(containerResource.getMetadataResourceFork(), "Should not be creating a shape tree instance on an unmanaged target container");

        final ShapeTreeLocator containerLocator = this.getShapeTreeLocatorFromResource(containerResource.getMetadataResourceFork());
        this.ensureShapeTreeLocatorExists(containerLocator, "Cannot have a shape tree metadata resource without a shape tree locator with at least one shape tree location");

        // Get the shape tree associated that specifies what resources can be contained by the target container (st:contains)
        final ShapeTreeLocation containingLocation = containerLocator.getContainingShapeTreeLocation();

        if (containingLocation == null) {
            // If there is no containing shape tree for the target container, then the request is valid and can
            // be passed straight through
            return Optional.empty();
        }

        final URL containerShapeTreeUrl = containingLocation.getShapeTree();
        final ShapeTree containerShapeTree = ShapeTreeFactory.getShapeTree(containerShapeTreeUrl);

        final URL targetShapeTree = this.getIncomingTargetShapeTreeHint(shapeTreeRequest, targetResourceUrl);
        final URL incomingFocusNode = this.getIncomingResolvedFocusNode(shapeTreeRequest, targetResourceUrl);
        final Graph incomingBodyGraph = this.getIncomingBodyGraph(shapeTreeRequest, targetResourceUrl, null);

        final ValidationResult validationResult = containerShapeTree.validateContainedResource(proposedName, shapeTreeRequest.getResourceType(), targetShapeTree, incomingBodyGraph, incomingFocusNode);
        if (Boolean.FALSE.equals(validationResult.isValid())) {
            return this.failValidation(validationResult);
        }

        log.debug("Creating shape tree instance at {}", targetResourceUrl);

        final ShapeTreeResource createdResource = new ShapeTreeResource(targetResourceUrl, this.resourceAccessor, targetResource.getShapeTreeContext(), shapeTreeRequest);

        final ShapeTreeLocation rootShapeTreeLocation = this.getRootShapeTreeLocation(targetResource.getShapeTreeContext(), containingLocation);
        this.ensureShapeTreeLocationExists(rootShapeTreeLocation, "Unable to find root shape tree location at " + containingLocation.getRootShapeTreeLocation());

        log.debug("Assigning shape tree to created resource: {}", createdResource.getMetadataResourceFork().getUrl());
        // Note: By providing the positive advance validationResult, we let the assignment operation know that validation
        // has already been performed with a positive result, and avoid having it perform the validation a second time
        final Optional<DocumentResponse> assignResult = this.assignShapeTreeToResource(createdResource, targetResource.getShapeTreeContext(), null, rootShapeTreeLocation, containingLocation, validationResult);
        if (assignResult.isPresent()) { return assignResult; }

        return Optional.of(this.successfulValidation());
    }

    protected Optional<DocumentResponse> updateShapeTreeInstance(final ShapeTreeResource targetResource, final ShapeTreeContext shapeTreeContext, final ShapeTreeRequest shapeTreeRequest) throws ShapeTreeException {


        this.ensureShapeTreeResourceExists(targetResource.getPrimaryResourceFork(),"Target resource to update not found");
        this.ensureShapeTreeResourceExists(targetResource.getMetadataResourceFork(), "Should not be updating an unmanaged resource as a shape tree instance");

        final ShapeTreeLocator locator = this.getShapeTreeLocatorFromResource(targetResource.getMetadataResourceFork());
        this.ensureShapeTreeLocatorExists(locator, "Cannot have a shape tree metadata resource without a shape tree locator with at least one shape tree location");

        for (final ShapeTreeLocation location : locator.getLocations()) {

            // Evaluate the update against each ShapeTreeLocation managing the resource.
            // All must pass for the update to validate
            final ShapeTree shapeTree = ShapeTreeFactory.getShapeTree(location.getShapeTree());
            final URL primaryUrl = targetResource.getPrimaryResourceFork().getUrl();
            final ValidationResult validationResult = shapeTree.validateResource(null, shapeTreeRequest.getResourceType(), this.getIncomingBodyGraph(shapeTreeRequest, primaryUrl, targetResource.getPrimaryResourceFork()), this.getIncomingResolvedFocusNode(shapeTreeRequest, primaryUrl));
            if (Boolean.FALSE.equals(validationResult.isValid())) { return this.failValidation(validationResult); }

        }

        // No issues with validation, so the request is passed along
        return Optional.empty();

    }

    protected Optional<DocumentResponse> deleteShapeTreeInstance() {
        // Nothing to validate in a delete request, so the request is passed along
        return Optional.empty();
    }

    protected Optional<DocumentResponse> assignShapeTreeToResource(final ShapeTreeResource primaryResource,
                                                                   final ShapeTreeContext shapeTreeContext,
                                                                   final ShapeTreeLocator rootLocator,
                                                                   final ShapeTreeLocation rootLocation,
                                                                   final ShapeTreeLocation parentLocation,
                                                                   final ValidationResult advanceValidationResult)
            throws ShapeTreeException {

        ShapeTree primaryResourceShapeTree = null;
        ShapeTreeLocator primaryResourceLocator = null;
        URL primaryResourceMatchingNode = null;
        ShapeTreeLocation primaryResourceLocation = null;
        Optional<DocumentResponse> validationResponse = null;

        this.ensureValidationResultIsUsableForAssignment(advanceValidationResult, "Invalid advance validation result provided for resource assignment");
        if (advanceValidationResult != null) { primaryResourceShapeTree = advanceValidationResult.getMatchingShapeTree(); }
        if (advanceValidationResult != null) { primaryResourceMatchingNode = advanceValidationResult.getMatchingFocusNode(); }

        if (this.atRootOfPlantHierarchy(rootLocation, primaryResource.getPrimaryResourceFork())) {

            // If we are at the root of the plant hierarchy we don't need to validate the primary resource against
            // a shape tree managing a parent container. We only need to validate the primary resource against
            // the shape tree that is being planted at the root to ensure it conforms.
            primaryResourceShapeTree = ShapeTreeFactory.getShapeTree(rootLocation.getShapeTree());
            if (advanceValidationResult == null) {    // If this validation wasn't performed in advance
                final ValidationResult validationResult = primaryResourceShapeTree.validateResource(primaryResource.getPrimaryResourceFork());
                if (Boolean.FALSE.equals(validationResult.isValid())) { return this.failValidation(validationResult); }
                primaryResourceMatchingNode = validationResult.getMatchingFocusNode();
            }

        } else {

            // Not at the root of the plant hierarchy. Validate proposed resource against the shape tree
            // managing the parent container, then extract the matching shape tree and focus node on success
            if (advanceValidationResult == null) {    // If this validation wasn't performed in advance
                final ShapeTree parentShapeTree = ShapeTreeFactory.getShapeTree(parentLocation.getShapeTree());
                final ValidationResult validationResult = parentShapeTree.validateContainedResource(primaryResource.getPrimaryResourceFork());
                if (Boolean.FALSE.equals(validationResult.isValid())) { return this.failValidation(validationResult); }
                primaryResourceShapeTree = validationResult.getMatchingShapeTree();
                primaryResourceMatchingNode = validationResult.getMatchingFocusNode();
            }

        }

        primaryResourceLocator = this.getPrimaryResourceLocatorForAssignment(primaryResource, rootLocator, rootLocation);
        primaryResourceLocation = this.getPrimaryResourceLocationForAssignment(primaryResource.getPrimaryResourceFork(), primaryResourceLocator, rootLocation, primaryResourceShapeTree, primaryResourceMatchingNode);

        // If the primary resource is a container, and its shape tree specifies its contents with st:contains
        // Recursively traverse the hierarchy and perform shape tree assignment
        if (primaryResource.getPrimaryResourceFork().isContainer() && primaryResourceShapeTree.getContains() != null && !primaryResourceShapeTree.getContains().isEmpty()) {

            // If the container is not empty, perform a recursive, depth first validation and assignment for each
            // contained resource by recursively calling this method (assignShapeTreeToResource)
            // TODO - Provide a configurable maximum limit on contained resources for a recursive plant, generate ShapeTreeException
            final List<ShapeTreeResource> containedResources = this.resourceAccessor.getContainedResources(shapeTreeContext, primaryResource.getPrimaryResourceFork().getUrl());
            if (!containedResources.isEmpty()) {
                Collections.sort(containedResources, new SortByShapeTreeResourceType());  // Evaluate containers, then resources
                for (final ShapeTreeResource containedResource : containedResources) {
                    validationResponse = this.assignShapeTreeToResource(containedResource, shapeTreeContext, null, rootLocation, primaryResourceLocation, null);
                    if (validationResponse.isPresent()) { return validationResponse; }
                }
            }
        }
        primaryResource.createOrUpdateMetadataResource(primaryResourceLocator);

        return Optional.empty();

    }

    protected Optional<DocumentResponse> unassignShapeTreeFromResource(final ShapeTreeResource primaryResource, final ShapeTreeContext shapeTreeContext,
                                                                       final ShapeTreeLocation rootLocation) throws ShapeTreeException {


        this.ensureShapeTreeResourceExists(primaryResource.getPrimaryResourceFork(), "Cannot unassign location from non-existent primary resource");
        this.ensureShapeTreeResourceExists(primaryResource.getMetadataResourceFork(), "Cannot unassign location from non-existent metadata resource");

        final ShapeTreeLocator primaryResourceLocator = this.getShapeTreeLocatorFromResource(primaryResource.getMetadataResourceFork());
        final ShapeTreeLocation removeLocation = this.getShapeTreeLocationForRoot(primaryResourceLocator, rootLocation);
        final ShapeTree primaryResourceShapeTree = ShapeTreeFactory.getShapeTree(removeLocation.getShapeTree());

        Optional<DocumentResponse> validationResponse = null;

        // If the primary resource is a container, and its shape tree specifies its contents with st:contains
        // Recursively traverse the hierarchy and perform shape tree unassignment
        if (primaryResource.getPrimaryResourceFork().isContainer() && primaryResourceShapeTree.getContains() != null && !primaryResourceShapeTree.getContains().isEmpty()) {

            // TODO - Should there also be a configurable maximum limit on unplanting?
            final List<ShapeTreeResource> containedResources = this.resourceAccessor.getContainedResources(shapeTreeContext, primaryResource.getPrimaryResourceFork().getUrl());
            // If the container is not empty
            if (!containedResources.isEmpty()) {
                // Sort contained resources so that containers are evaluated first, then resources
                Collections.sort(containedResources, new SortByShapeTreeResourceType());
                // Perform a depth first unassignment for each contained resource
                for (final ShapeTreeResource containedResource : containedResources) {
                    // Recursively call this function on the contained resource
                    validationResponse = this.unassignShapeTreeFromResource(containedResource, shapeTreeContext, rootLocation);
                    if (validationResponse.isPresent()) { return validationResponse; }
                }
            }
        }

        primaryResourceLocator.removeShapeTreeLocation(removeLocation);

        this.deleteOrUpdateMetadataResource(shapeTreeContext, primaryResource.getMetadataResourceFork(), primaryResourceLocator);

        return Optional.empty();

    }

    /**
     * Builds a ShapeTreeContext from the incoming request.  Specifically it retrieves
     * the incoming Authorization header and stashes that value for use on any additional requests made during
     * validation.
     * @param shapeTreeRequest Incoming request
     * @return ShapeTreeContext object populated with authentication details, if present
     */
    protected ShapeTreeContext buildContextFromRequest(final ShapeTreeRequest shapeTreeRequest) {
        return new ShapeTreeContext(shapeTreeRequest.getHeaderValue(HttpHeaders.AUTHORIZATION.getValue()));
    }

    /**
     * This determines the type of resource being processed.
     *
     * Initial test is based on the incoming request headers, specifically the Content-Type header.
     * If the content type is not one of the accepted RDF types, it will be treated as a NON-RDF source.
     *
     * Then the determination becomes whether or not the resource is a container.
     *
     * If it is a PATCH or PUT and the URL provided already exists, then the existing resource's Link header(s)
     * are used to determine if it is a container or not.
     *
     * If it is a POST or if the resource does not already exist, the incoming request Link header(s) are relied
     * upon.
     *
     * @param shapeTreeRequest The current incoming request
     * @param existingResource The resource located at the incoming request's URL
     * @return ShapeTreeResourceType aligning to current request
     * @throws ShapeTreeException ShapeTreeException throw, specifically if Content-Type is not included on request
     */
    protected ShapeTreeResourceType determineResourceType(final ShapeTreeRequest shapeTreeRequest, final ShapeTreeResource existingResource) throws ShapeTreeException {
        final boolean isNonRdf;
        if (!shapeTreeRequest.getMethod().equals(DELETE)) {
            final String incomingRequestContentType = shapeTreeRequest.getContentType();
            // Ensure a content-type is present
            if (incomingRequestContentType == null) {
                throw new ShapeTreeException(400, "Content-Type is required");
            }

            isNonRdf = this.determineIsNonRdfSource(incomingRequestContentType);

        } else {
            isNonRdf = false;
        }

        if (isNonRdf) {
            return ShapeTreeResourceType.NON_RDF;
        }

        boolean isContainer = false;
        final boolean resourceAlreadyExists = existingResource.getPrimaryResourceFork().wasSuccessful();
        if ((shapeTreeRequest.getMethod().equals(PUT) || shapeTreeRequest.getMethod().equals(PATCH)) && resourceAlreadyExists) {
            isContainer = existingResource.getPrimaryResourceFork().isContainer();
        } else if (shapeTreeRequest.getLinkHeaders() != null) {
            isContainer = this.getIsContainerFromRequest(shapeTreeRequest);
        }

        return isContainer ? ShapeTreeResourceType.CONTAINER : ShapeTreeResourceType.RESOURCE;
    }

    /**
     * Normalizes the BaseURL to use for a request based on the incoming request.
     * @param url URL of request
     * @param requestedName Requested name of resource (provided on created resources via POST)
     * @param resourceType Description of resource (Container, NonRDF, Resource)
     * @return BaseURL to use for RDF Graphs
     * @throws ShapeTreeException ShapeTreeException
     */
    protected URL normalizeSolidResourceUrl(final URL url, final String requestedName, final ShapeTreeResourceType resourceType) throws ShapeTreeException {
        String urlString = url.toString();
        if (requestedName != null) {
            urlString += requestedName;
        }
        if (resourceType == ShapeTreeResourceType.CONTAINER && !urlString.endsWith("/")) {
            urlString += "/";
        }
        try {
            return new URL(urlString);
        } catch (final MalformedURLException ex) {
            throw new ShapeTreeException(500, "normalized to malformed URL <" + urlString + "> - " + ex.getMessage());
        }
    }

    /**
     * Loads body of request into graph
     * @param shapeTreeRequest Request
     * @param baseUrl BaseURL to use for graph
     * @param targetResource
     * @return Graph representation of request body
     * @throws ShapeTreeException ShapeTreeException
     */
    protected Graph getIncomingBodyGraph(final ShapeTreeRequest shapeTreeRequest, final URL baseUrl, final ShapeTreeResource.Fork targetResource) throws ShapeTreeException {
        log.debug("Reading request body into graph with baseUrl {}", baseUrl);

        if ((shapeTreeRequest.getResourceType() == ShapeTreeResourceType.NON_RDF
                && !shapeTreeRequest.getContentType().equalsIgnoreCase("application/sparql-update"))
                || shapeTreeRequest.getBody() == null
                || shapeTreeRequest.getBody().length() == 0) {
            return null;
        }

        Graph targetResourceGraph = null;

        if (shapeTreeRequest.getMethod().equals(PATCH)) {

            // In the event of a SPARQL PATCH, we get the SPARQL query and evaluate it, passing the
            // resultant graph back to the caller

            if (targetResource != null) {
                targetResourceGraph = this.getGraphForResource(targetResource, baseUrl);
            }

            if (targetResourceGraph == null) {   // if the target resource doesn't exist or has no content
                log.debug("Existing target resource graph to patch does not exist.  Creating an empty graph.");
                targetResourceGraph = ModelFactory.createDefaultModel().getGraph();
            }

            // Perform a SPARQL update locally to ensure that resulting graph validates against ShapeTree
            final UpdateRequest updateRequest = UpdateFactory.create(shapeTreeRequest.getBody(), baseUrl.toString());
            UpdateAction.execute(updateRequest, targetResourceGraph);

            if (targetResourceGraph == null) {
                throw new ShapeTreeException(400, "No graph after update");
            }

        } else {
            targetResourceGraph = GraphHelper.readStringIntoGraph(urlToUri(baseUrl), shapeTreeRequest.getBody(), shapeTreeRequest.getContentType());
        }

        return targetResourceGraph;
    }

    /**
     * Gets focus node from request header
     * @param shapeTreeRequest Request
     * @param baseUrl Base URL for use on relative focus nodes
     * @return URL of focus node
     * @throws IOException IOException
     */
    protected URL getIncomingResolvedFocusNode(final ShapeTreeRequest shapeTreeRequest, final URL baseUrl) throws ShapeTreeException {
        final String focusNode = shapeTreeRequest.getLinkHeaders().firstValue(LinkRelations.FOCUS_NODE.getValue()).orElse(null);
        if (focusNode != null) {
            try {
                return new URL(baseUrl, focusNode);
            } catch (final MalformedURLException e) {
                throw new ShapeTreeException(500, "Malformed focus node when resolving <" + focusNode + "> against <" + baseUrl + ">");
            }
        }
        return null;
    }

    /**
     * Gets target shape tree / hint from request header
     * @param shapeTreeRequest Request
     * @return URL value of target shape tree
     * @throws ShapeTreeException ShapeTreeException
     */
    protected URL getIncomingTargetShapeTreeHint(final ShapeTreeRequest shapeTreeRequest, final URL baseUrl) throws ShapeTreeException {
        final String targetShapeTree = shapeTreeRequest.getLinkHeaders().firstValue(LinkRelations.TARGET_SHAPETREE.getValue()).orElse(null);
        if (targetShapeTree != null) {
            try {
                return new URL(targetShapeTree);
            } catch (final MalformedURLException e) {
                throw new ShapeTreeException(500, "Malformed focus node when resolving <" + targetShapeTree + "> against <" + baseUrl + ">");
            }
        }
        return null;
    }

    /**
     * Determines if a resource should be treated as a container based on its request Link headers
     * @param shapeTreeRequest Request
     * @return Is the resource a container?
     */
    protected Boolean getIsContainerFromRequest(final ShapeTreeRequest shapeTreeRequest) {
        // First try to determine based on link headers
        if (shapeTreeRequest.getLinkHeaders() != null) {
            final List<String> typeLinks = shapeTreeRequest.getLinkHeaders().allValues(LinkRelations.TYPE.getValue());
            if (typeLinks.size() != 0) {
                return (typeLinks.contains(LdpVocabulary.CONTAINER) ||
                        typeLinks.contains(LdpVocabulary.BASIC_CONTAINER));
            }
        }
        // As a secondary attempt, use slash path semantics
        return shapeTreeRequest.getUrl().getPath().endsWith("/");
    }

    /**
     * Determines whether a content type is a supported RDF type
     * @param incomingRequestContentType Content type to test
     * @return Boolean indicating whether it is RDF or not
     */
    protected boolean determineIsNonRdfSource(final String incomingRequestContentType) {
        return (!this.supportedRDFContentTypes.contains(incomingRequestContentType.toLowerCase()) &&
                !this.supportedSPARQLContentTypes.contains(incomingRequestContentType.toLowerCase()));
    }

    /**
     * Returns parent container URL for a given resource
     * @param primaryResource Resource
     * @return URL to the resource's parent container
     */
    protected URL getParentContainerUrl(final ShapeTreeResource.Primary primaryResource) throws ShapeTreeException {
        final String rel = primaryResource.isContainer() ? ".." : ".";
        try {
            return new URL(primaryResource.getUrl(), rel);
        } catch (final MalformedURLException e) {
            throw new ShapeTreeException(500, "Malformed focus node when resolving <" + rel + "> against <" + primaryResource.getUrl() + ">");
        }
    }

    /**
     * Returns resource name from a resource URL
     * @param primaryResource Resource
     * @return Resource name
     */
    protected String getRequestResourceName(final ShapeTreeResource.Primary primaryResource) throws ShapeTreeException {

        String resourceName = primaryResource.getUrl().toString().replace(this.getParentContainerUrl(primaryResource).toString(), "");

        if (resourceName.equals("/")) { return "/"; }

        // if this is a container, trim the trailing slash
        if (resourceName.endsWith("/")) {
            resourceName = resourceName.substring(0, resourceName.length() - 1);
        }
        return resourceName;

    }

    /**
     * Returns a graph representation of a resource
     * @param resource Resource to get graph of
     * @param baseUrl BaseURL to use for triples
     * @return Graph representation of resource
     * @throws ShapeTreeException ShapeTreeException
     */
    protected Graph getGraphForResource(final ShapeTreeResource.Fork resource, final URL baseUrl) throws ShapeTreeException {

        if (!resource.wasSuccessful()) return null;
        final URI baseUri = urlToUri(baseUrl);
        return GraphHelper.readStringIntoGraph(baseUri, resource.getBody(), resource.getAttributes().firstValue(HttpHeaders.CONTENT_TYPE.getValue()).orElse(null));
    }

    protected ShapeTreeLocator getShapeTreeLocatorFromRequest(final ShapeTreeRequest shapeTreeRequest, final ShapeTreeResource.Metadata metadataResource) throws ShapeTreeException {

        final Graph incomingBodyGraph = this.getIncomingBodyGraph(shapeTreeRequest, this.normalizeSolidResourceUrl(shapeTreeRequest.getUrl(), null, ShapeTreeResourceType.RESOURCE), metadataResource);
        if (incomingBodyGraph == null) { return null; }
        return ShapeTreeLocator.getShapeTreeLocatorFromGraph(shapeTreeRequest.getUrl(), incomingBodyGraph);
    }

    protected ShapeTreeLocator getShapeTreeLocatorFromResource(final ShapeTreeResource.Metadata metadataResource) throws ShapeTreeException {

        if (!metadataResource.wasSuccessful()) { return null; }
        final Graph metadataResourceGraph = this.getGraphForResource(metadataResource, this.normalizeSolidResourceUrl(metadataResource.getUrl(), null, metadataResource.getResourceType()));
        if (metadataResourceGraph == null) { return null; }
        return ShapeTreeLocator.getShapeTreeLocatorFromGraph(metadataResource.getUrl(), metadataResourceGraph);

    }

    // Given a root location, lookup the corresponding location in a shape tree locator that has the same root location
    protected ShapeTreeLocation getShapeTreeLocationForRoot(final ShapeTreeLocator locator, final ShapeTreeLocation rootLocation) {

        if (locator.getLocations() == null || locator.getLocations().isEmpty()) { return null; }

        for (final ShapeTreeLocation location : locator.getLocations()) {
            if (rootLocation.getUrl().equals(location.getRootShapeTreeLocation())) {
                return location;
            }
        }
        return null;
    }

    private void deleteOrUpdateMetadataResource(final ShapeTreeContext shapeTreeContext,
                                                final ShapeTreeResource.Metadata primaryMetadataResource,
                                                final ShapeTreeLocator primaryResourceLocator) throws ShapeTreeException {

        if (primaryResourceLocator.getLocations().isEmpty()) {
            final DocumentResponse response = this.resourceAccessor.deleteResource(shapeTreeContext, primaryMetadataResource);
            this.ensureDeleteIsSuccessful(response);
        } else {
            // Update the existing metadata resource for the primary resource
            this.resourceAccessor.updateResource(shapeTreeContext, "PUT", primaryMetadataResource, primaryResourceLocator.getGraph().toString());
        }

    }

    private ShapeTreeLocator getPrimaryResourceLocatorForAssignment(final ShapeTreeResource primaryResource,
                                                                    final ShapeTreeLocator rootLocator,
                                                                    final ShapeTreeLocation rootLocation) throws ShapeTreeException {

        ShapeTreeLocator primaryResourceLocator = null;

        // When at the top of the plant hierarchy, use the root locator from the initial plant request body
        if (this.atRootOfPlantHierarchy(rootLocation, primaryResource.getPrimaryResourceFork())) { return rootLocator; }

        if (!primaryResource.getMetadataResourceFork().wasSuccessful()) {
            // If the existing metadata resource doesn't exist make a new shape tree locator
            primaryResourceLocator = new ShapeTreeLocator(primaryResource.getMetadataResourceFork().getUrl());
        } else {
            // Get the existing shape tree locator from the metadata resource graph
            final Graph primaryMetadataGraph = this.getGraphForResource(primaryResource.getPrimaryResourceFork(), primaryResource.getMetadataResourceFork().getUrl());
            primaryResourceLocator = ShapeTreeLocator.getShapeTreeLocatorFromGraph(primaryResource.getMetadataResourceFork().getUrl(), primaryMetadataGraph);
        }

        return primaryResourceLocator;

    }

    private ShapeTreeLocation getPrimaryResourceLocationForAssignment(final ShapeTreeResource.Fork primaryResource,
                                                                      final ShapeTreeLocator primaryResourceLocator,
                                                                      final ShapeTreeLocation rootLocation,
                                                                      final ShapeTree primaryResourceShapeTree,
                                                                      final URL primaryResourceMatchingNode) throws ShapeTreeException {

        URL primaryResourceLocationUrl = null;

        if (!this.atRootOfPlantHierarchy(rootLocation, primaryResource)) {
            // Mint a new location URL, since it wouldn't have been passed in the initial request body
            primaryResourceLocationUrl = primaryResourceLocator.mintLocation();
        }

        // Build the primary resource location
        final URL matchingNode = primaryResourceMatchingNode;
        final ShapeTreeLocation primaryResourceLocation = new ShapeTreeLocation(primaryResourceShapeTree.getId(),
                primaryResource.getUrl(),
                rootLocation.getUrl(),
                matchingNode,
                primaryResourceShapeTree.getShape(),
                primaryResourceLocationUrl);

        if (!this.atRootOfPlantHierarchy(rootLocation, primaryResource)) {
            // Add the shape tree location to the shape tree locator for the primary resource
            primaryResourceLocator.addShapeTreeLocation(primaryResourceLocation);
        }

        return primaryResourceLocation;

    }

    private boolean atRootOfPlantHierarchy(final ShapeTreeLocation rootLocation, final ShapeTreeResource.Fork primaryResource) {
        return rootLocation.getManagedResource().toString().equals(primaryResource.getUrl().toString());
    }

    // Return a root shape tree locator associated with a given shape tree location
    private ShapeTreeLocator getRootShapeTreeLocator(final ShapeTreeContext shapeTreeContext, final ShapeTreeLocation location) throws ShapeTreeException {

        final URL rootLocationUrl = location.getRootShapeTreeLocation();
        final ShapeTreeResource.Metadata locatorResource = new ShapeTreeResource(rootLocationUrl, this.resourceAccessor, shapeTreeContext).getMetadataResourceFork(); // this.resourceAccessor.getResource(shapeTreeContext, rootLocationBaseUrl);
        // @@ ensureShapeTreeResourceExists(locatorResource, "Unable to find root shape tree locator");

        return this.getShapeTreeLocatorFromResource(locatorResource);

    }

    // Return a root shape tree locator associated with a given shape tree location
    private ShapeTreeLocation getRootShapeTreeLocation(final ShapeTreeContext shapeTreeContext, final ShapeTreeLocation location) throws ShapeTreeException {

        final ShapeTreeLocator rootLocator = this.getRootShapeTreeLocator(shapeTreeContext, location);

        for (final ShapeTreeLocation rootLocation : rootLocator.getLocations()) {
            if (rootLocation.getUrl() != null && rootLocation.getUrl().equals(location.getRootShapeTreeLocation())) {
                return rootLocation;
            }
        }
        return null;

    }

    private void ensureValidationResultIsUsableForAssignment(final ValidationResult validationResult, final String message) throws ShapeTreeException {
        // Null is a usable state of the validation result in the context of assignment
        if (validationResult != null &&
                (validationResult.getValid() == null ||
                        validationResult.getMatchingShapeTree() == null ||
                        validationResult.getValidatingShapeTree() == null)) {
            throw new ShapeTreeException(400, message);
        }
    }

    private void ensureShapeTreeResourceExists(final ShapeTreeResource.Fork shapeTreeResource, final String message) throws ShapeTreeException {
        if (shapeTreeResource == null || !shapeTreeResource.wasSuccessful()) {
            throw new ShapeTreeException(404, message);
        }
    }

    private void ensureRequestResourceIsContainer(final ShapeTreeResource.Primary shapeTreeResource, final String message) throws ShapeTreeException {
        if (!shapeTreeResource.isContainer()) {
            throw new ShapeTreeException(400, message);
        }
    }

    private void ensureTargetPrimaryResourceDoesNotExist(final ShapeTreeContext shapeTreeContext, final URL targetResourceUrl, final String message) throws ShapeTreeException {
        final ShapeTreeResource targetResource = new ShapeTreeResource(targetResourceUrl, this.resourceAccessor, shapeTreeContext);
        if (targetResource.wasCreatedFromMetadata() || targetResource.getPrimaryResourceFork().wasSuccessful()) {
            throw new ShapeTreeException(409, message);
        }
    }

    private void ensureShapeTreeLocatorExists(final ShapeTreeLocator locator, final String message) throws ShapeTreeException {
        if (locator == null || locator.getLocations() == null || locator.getLocations().isEmpty()) {
            throw new ShapeTreeException(400, message);
        }
    }

    private void ensureShapeTreeLocationExists(final ShapeTreeLocation location, final String message) throws ShapeTreeException {
        if (location == null) {
            throw new ShapeTreeException(400, message);
        }
    }

    private void ensureAllRemovedFromLocatorByDelete(final ShapeTreeRequest shapeTreeRequest) throws ShapeTreeException {
        if (!shapeTreeRequest.getMethod().equals(DELETE)) {
            throw new ShapeTreeException(500, "Removal of all ShapeTreeLocations from a ShapeTreeLocator MUST use HTTP DELETE");
        }
    }

    private void ensureRemovedLocationsAreRootLocations(final ShapeTreeLocatorDelta delta) throws ShapeTreeException {
        for (final ShapeTreeLocation removedLocation : delta.getRemovedLocations()) {
            if (!removedLocation.isRootLocation()) {
                throw new ShapeTreeException(500, "Cannot remove non-root ShapeTreeLocation: " + removedLocation.getUrl().toString() + ". Must unplant root location at: " + removedLocation.getRootShapeTreeLocation().toString());
            }
        }
    }

    private void ensureUpdatedLocationsAreRootLocations(final ShapeTreeLocatorDelta delta) throws ShapeTreeException {
        for (final ShapeTreeLocation updatedLocation : delta.getUpdatedLocations()) {
            if (!updatedLocation.isRootLocation()) {
                throw new ShapeTreeException(500, "Cannot update non-root ShapeTreeLocation: " + updatedLocation.getUrl().toString() + ". Must update root location at: " + updatedLocation.getRootShapeTreeLocation().toString());
            }
        }
    }

    private void ensureDeleteIsSuccessful(final DocumentResponse response) throws ShapeTreeException {
        final List<Integer> successCodes = Arrays.asList(202,204,200);
        if (!successCodes.contains(response.getStatusCode())) {
            throw new ShapeTreeException(500, "Failed to delete metadata resource. Received " + response.getStatusCode() + ": " + response.getBody());
        }
    }

    private DocumentResponse successfulValidation() {
        return new DocumentResponse(new ResourceAttributes(), "OK", 201);
    }

    private Optional<DocumentResponse> failValidation(final ValidationResult validationResult) {
        return Optional.of(new DocumentResponse(new ResourceAttributes(), validationResult.getMessage(),422));
    }
}



class SortByShapeTreeResourceType implements Comparator<ShapeTreeResource>, Serializable {

    // Used for sorting by shape tree resource type with the following order
    // 1. Containers
    // 2. Resources
    // 3. Non-RDF Resources

    @SneakyThrows // @@ These are known to be user-owned
    public int compare (final ShapeTreeResource a, final ShapeTreeResource b) {
        return a.getPrimaryResourceFork().getResourceType().compareTo(b.getPrimaryResourceFork().getResourceType());
    }

}
