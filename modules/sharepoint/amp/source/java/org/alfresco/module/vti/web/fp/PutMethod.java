/*
 * #%L
 * Alfresco Sharepoint Protocol
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.module.vti.web.fp;

import org.alfresco.module.vti.handler.alfresco.VtiPathHelper;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.repository.NodeRef;

/**
 * WebDAV PUT method.
 * 
 * @author Matt Ward
 */
public class PutMethod extends org.alfresco.repo.webdav.PutMethod
{
    private String alfrescoContext;
    private VtiPathHelper pathHelper;
    
    public PutMethod(VtiPathHelper pathHelper)
    {
        this.alfrescoContext = pathHelper.getAlfrescoContext();
        this.pathHelper = pathHelper;
    }
    
    /**
     * @see org.alfresco.repo.webdav.WebDAVMethod#getNodeForPath(org.alfresco.service.cmr.repository.NodeRef, java.lang.String)
     */
    @Override
    protected FileInfo getNodeForPath(NodeRef rootNodeRef, String path) throws FileNotFoundException
    {
        FileInfo nodeInfo = pathHelper.resolvePathFileInfo(path);
        if (nodeInfo == null)
        {
        	throw new FileNotFoundException(path);
        }
        FileInfo workingCopy = getWorkingCopy(nodeInfo.getNodeRef());
        return workingCopy != null ? workingCopy : nodeInfo;
    }
    
    /**
     * Returns the path, excluding the Servlet Context (if present)
     * @see org.alfresco.repo.webdav.WebDAVMethod#getPath()
     */
    @Override
    public String getPath()
    {
       return AbstractMethod.getPathWithoutContext(alfrescoContext, m_request);
    }
}
