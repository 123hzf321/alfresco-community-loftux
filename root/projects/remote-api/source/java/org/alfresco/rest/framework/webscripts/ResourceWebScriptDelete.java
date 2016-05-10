package org.alfresco.rest.framework.webscripts;

import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.rest.framework.core.ResourceLocator;
import org.alfresco.rest.framework.core.ResourceMetadata;
import org.alfresco.rest.framework.core.ResourceOperation;
import org.alfresco.rest.framework.core.ResourceWithMetadata;
import org.alfresco.rest.framework.core.exceptions.DeletedResourceException;
import org.alfresco.rest.framework.core.exceptions.UnsupportedResourceOperationException;
import org.alfresco.rest.framework.resource.actions.interfaces.BinaryResourceAction;
import org.alfresco.rest.framework.resource.actions.interfaces.EntityResourceAction;
import org.alfresco.rest.framework.resource.actions.interfaces.RelationshipResourceAction;
import org.alfresco.rest.framework.resource.actions.interfaces.RelationshipResourceBinaryAction;
import org.alfresco.rest.framework.resource.parameters.Params;
import org.apache.commons.lang.StringUtils;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.http.HttpMethod;

/**
 * Handles the HTTP DELETE for a Resource
 * 
 * @author Gethin James
 */
public class ResourceWebScriptDelete extends AbstractResourceWebScript implements ParamsExtractor
{

    public ResourceWebScriptDelete()
    {
        super();
        setHttpMethod(HttpMethod.DELETE);
        setParamsExtractor(this);
    }

    @Override
    public Params extractParams(ResourceMetadata resourceMeta, WebScriptRequest req)
    {
        String entityId = req.getServiceMatch().getTemplateVars().get(ResourceLocator.ENTITY_ID);
        String relationshipId = req.getServiceMatch().getTemplateVars().get(ResourceLocator.RELATIONSHIP_ID);
        
        switch (resourceMeta.getType())
        {
            case ENTITY:
                 if (StringUtils.isBlank(entityId))
                 {
                   throw new UnsupportedResourceOperationException("DELETE is executed against the instance URL");              
                 } 
                 else
                 {
                   return Params.valueOf(entityId, relationshipId);

                 }
            case RELATIONSHIP:

                if (StringUtils.isBlank(relationshipId))
                {
                  throw new UnsupportedResourceOperationException("DELETE is executed against the instance URL");                 
                } 
                else
                {
                  return Params.valueOf(entityId, relationshipId);
                }   
            case PROPERTY:
                final String resourceName = req.getServiceMatch().getTemplateVars().get(ResourceLocator.RELATIONSHIP_RESOURCE);
                final String propertyName = req.getServiceMatch().getTemplateVars().get(ResourceLocator.PROPERTY);

                if (StringUtils.isNotBlank(entityId) && StringUtils.isNotBlank(resourceName))
                {
                    if (StringUtils.isNotBlank(propertyName))
                    {
                        return Params.valueOf(entityId, relationshipId, null, null, propertyName, null, null);
                    }
                    else
                    {
                        return Params.valueOf(entityId, null, null, null, resourceName, null, null);
                    }
                }
                //Fall through to unsupported.
            default:
                throw new UnsupportedResourceOperationException("DELETE not supported for Actions");
        }
    }

    /**
     * Executes the action on the resource
     * @param resource ResourceWithMetadata
     * @param params parameters to use
     * @return anObject the result of the execute
     */
    private void executeInternal(ResourceWithMetadata resource, Params params)
    {
        switch (resource.getMetaData().getType())
        {
            case ENTITY:
                if (resource.getMetaData().isDeleted(EntityResourceAction.Delete.class))
                {
                    throw new DeletedResourceException("(DELETE) "+resource.getMetaData().getUniqueId());
                }
                EntityResourceAction.Delete entityDeleter = (EntityResourceAction.Delete) resource.getResource();
                entityDeleter.delete(params.getEntityId(), params);
                //Don't pass anything to the callback - its just successful
                return;
            case RELATIONSHIP:
                if (resource.getMetaData().isDeleted(RelationshipResourceAction.Delete.class))
                {
                    throw new DeletedResourceException("(DELETE) "+resource.getMetaData().getUniqueId());
                }
                RelationshipResourceAction.Delete relationDeleter = (RelationshipResourceAction.Delete) resource.getResource();
                relationDeleter.delete(params.getEntityId(), params.getRelationshipId(), params);
                //Don't pass anything to the callback - its just successful
                return;
            case PROPERTY:
                if (BinaryResourceAction.Delete.class.isAssignableFrom(resource.getResource().getClass()))
                {
                    if (resource.getMetaData().isDeleted(BinaryResourceAction.Delete.class))
                    {
                        throw new DeletedResourceException("(DELETE) "+resource.getMetaData().getUniqueId());
                    }
                    BinaryResourceAction.Delete binDeleter = (BinaryResourceAction.Delete) resource.getResource();
                    binDeleter.deleteProperty(params.getEntityId(), params);
                    //Don't pass anything to the callback - its just successful
                    return;
                }
                if (RelationshipResourceBinaryAction.Delete.class.isAssignableFrom(resource.getResource().getClass()))
                {
                    if (resource.getMetaData().isDeleted(RelationshipResourceBinaryAction.Delete.class))
                    {
                        throw new DeletedResourceException("(DELETE) "+resource.getMetaData().getUniqueId());
                    }
                    RelationshipResourceBinaryAction.Delete binDeleter = (RelationshipResourceBinaryAction.Delete) resource.getResource();
                    binDeleter.deleteProperty(params.getEntityId(), params.getRelationshipId(), params);
                    //Don't pass anything to the callback - its just successful
                    return;
                }
            default:
                throw new UnsupportedResourceOperationException("DELETE not supported for Actions");
        }
    }
    
    @Override
    public void execute(final ResourceWithMetadata resource, final Params params, final ExecutionCallback executionCallback)
    {
        transactionService.getRetryingTransactionHelper().doInTransaction(
            new RetryingTransactionCallback<Void>()
            {
                @Override
                public Void execute() throws Throwable
                {
                    final ResourceOperation operation = resource.getMetaData().getOperation(HttpMethod.DELETE);
                    executeInternal(resource, params); //ignore return result
                    executionCallback.onSuccess(null, DEFAULT_JSON_CONTENT, operation.getSuccessStatus());
                    return null;
                }
            }, false, true);
    }

}
