/*
 * Copyright (C) 2005-2008 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.web.scripts.content;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.SocketException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.TempFileProvider;
import org.alfresco.web.scripts.AbstractWebScript;
import org.alfresco.web.scripts.Cache;
import org.alfresco.web.scripts.Status;
import org.alfresco.web.scripts.WebScriptException;
import org.alfresco.web.scripts.WebScriptRequest;
import org.alfresco.web.scripts.WebScriptResponse;
import org.alfresco.web.scripts.WebScriptStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.FileCopyUtils;

import de.schlichtherle.io.FileOutputStream;

/**
 * Web script 'type' that can be used when the binary data of a content property needs to be streamed back to the client
 * as the result of executing the web script.
 * 
 * @author Roy Wetherall
 */
public class StreamContent extends AbstractWebScript
{
    // Logger
    private static final Log logger = LogFactory.getLog(StreamContent.class);

 	/**
 	 * format definied by RFC 822, see http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3 
 	 */
 	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US);
    
    /** Services */
    protected PermissionService permissionService;
    protected NodeService nodeService;
    protected ContentService contentService;
    protected MimetypeService mimetypeService;    
    
    /** Cached file modified date */
    private Date resouceFileModifiedDate;

    /**
     * @param mimetypeService
     */
    public void setMimetypeService(MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService; 
    }

    /**
     * @param permissionService
     */
    public void setPermissionService(PermissionService permissionService)
    {
        this.permissionService = permissionService; 
    }

    /**
     * @param nodeService
     */
    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService; 
    }

    /**
     * @param contentService
     */
    public void setContentService(ContentService contentService)
    {
        this.contentService = contentService; 
    }    

    /**
     * @see org.alfresco.web.scripts.WebScript#execute(org.alfresco.web.scripts.WebScriptRequest, org.alfresco.web.scripts.WebScriptResponse)
     */
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException
    {
        // retrieve requested format
        String format = req.getFormat();

        try
        {
            // construct model for script / template
            Status status = new Status();
            Cache cache = new Cache(getDescription().getRequiredCache());
            Map<String, Object> model = executeImpl(req, status, cache);
            if (model == null)
            {
                model = new HashMap<String, Object>(8, 1.0f);
            }
            model.put("status", status);
            model.put("cache", cache);
            
            // execute script if it exists
            ScriptDetails executeScript = getExecuteScript(req.getContentType());
            if (executeScript != null)
            {
                if (logger.isDebugEnabled())
                    logger.debug("Executing script " + executeScript.getContent().getPathDescription());
                
                Map<String, Object> scriptModel = createScriptParameters(req, res, model);
                // add return model allowing script to add items to template model
                Map<String, Object> returnModel = new HashMap<String, Object>(8, 1.0f);
                scriptModel.put("model", returnModel);
                executeScript(executeScript.getContent(), scriptModel);
                mergeScriptModelIntoTemplateModel(returnModel, model);
            }
            
            // is a redirect to a status specific template required?
            if (status.getRedirect())
            {
                // create model for template rendering
                Map<String, Object> templateModel = createTemplateParameters(req, res, model);
                sendStatus(req, res, status, cache, format, templateModel);
            }
            else
            {         
                // Get the attachement property value    
                Boolean attachBoolean = (Boolean)model.get("attach");
                boolean attach = false;
                if (attachBoolean != null)
                {
                    attach = attachBoolean.booleanValue();
                }
                
                String contentPath = (String)model.get("contentPath");
                if (contentPath == null)
                {
                    // Get the content parameters from the model
                    NodeRef nodeRef = (NodeRef)model.get("contentNode");
                    if (nodeRef == null)
                    {
                        throw new WebScriptException("The content node was not specified so the content cannot be streamed to the client");
                    }
                    QName propertyQName = null;
                    String contentProperty = (String)model.get("contentProperty");
                    if (contentProperty == null)
                    {
                        // default to the standard content property
                        propertyQName = ContentModel.PROP_CONTENT;
                    }
                    else
                    {
                        propertyQName = QName.createQName(contentProperty);
                    }
                
                    // Stream the content
                    streamContent(req, res, nodeRef, propertyQName, attach);
                }
                else
                {
                    // Stream the content
                    streamContent(req, res, contentPath, attach);
                }
            }
        }
        catch(Throwable e)
        {
            throw createStatusException(e, req, res);
        }
    }
    
    /**
     * Merge script generated model into template-ready model
     * 
     * @param scriptModel  script model
     * @param templateModel  template model
     */
    final private void mergeScriptModelIntoTemplateModel(Map<String, Object> scriptModel, Map<String, Object> templateModel)
    {
        for (Map.Entry<String, Object> entry : scriptModel.entrySet())
        {
            // retrieve script model value
            Object value = entry.getValue();
            Object templateValue = getContainer().getScriptProcessor().unwrapValue(value);
            templateModel.put(entry.getKey(), templateValue);
        }
    }

    /**
     * Execute custom Java logic
     * 
     * @param req  Web Script request
     * @param status Web Script status
     * @return  custom service model
     * @deprecated
     */
    @SuppressWarnings("deprecation")
    protected Map<String, Object> executeImpl(WebScriptRequest req, WebScriptStatus status)
    {
        return null;
    }

    /**
     * Execute custom Java logic
     * 
     * @param req  Web Script request
     * @param status Web Script status
     * @return  custom service model
     * @deprecated
     */
    @SuppressWarnings("deprecation")
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status)
    {
        return executeImpl(req, new WebScriptStatus(status));
    }

    /**
     * Execute custom Java logic
     * 
     * @param  req  Web Script request
     * @param  status Web Script status
     * @param  cache  Web Script cache
     * @return  custom service model
     */
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        // NOTE: Redirect to those web scripts implemented before cache support and v2.9
        return executeImpl(req, status);
    }
    
    /**
     * Render a template (of given format) to the Web Script Response
     * 
     * @param format  template format (null, default format)  
     * @param model  data model to render
     * @param writer  where to output
     */
    final protected void renderFormatTemplate(String format, Map<String, Object> model, Writer writer)
    {
        format = (format == null) ? "" : format;
        String templatePath = getDescription().getId() + "." + format + ".ftl";

        if (logger.isDebugEnabled())
            logger.debug("Rendering template '" + templatePath + "'");

        renderTemplate(templatePath, model, writer);
    }
    
    /**
     * Streams the content on a given node's content property to the response of the web script.
     * 
     * @param req           request
     * @param res           response
     * @param nodeRef       the node reference
     * @param propertyQName the content property name
     * @param attach        indicates whether the content should be streamed as an attachment or not
     * @throws IOException 
     */
    protected void streamContent(WebScriptRequest req, WebScriptResponse res, NodeRef nodeRef, QName propertyQName, boolean attach)
        throws IOException
    {
        if (logger.isDebugEnabled())
            logger.debug("Retrieving content from node ref " + nodeRef.toString() + " (property: " + propertyQName.toString() + ") (attach: " + attach + ")");

        // check that the user has at least READ_CONTENT access - else redirect to the login page
        if (permissionService.hasPermission(nodeRef, PermissionService.READ_CONTENT) == AccessStatus.DENIED)
        {
            throw new WebScriptException(HttpServletResponse.SC_FORBIDDEN, "Permission denied");
        }
       
        // check If-Modified-Since header and set Last-Modified header as appropriate
        Date modified = (Date)nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIED);
        long modifiedSince = -1;
        String modifiedSinceStr = req.getHeader("If-Modified-Since");
        if (modifiedSinceStr != null)
        {
            try
            {
                modifiedSince = dateFormat.parse(modifiedSinceStr).getTime();
            }
            catch (ParseException e)
            {
                if (logger.isInfoEnabled())
                    logger.info("ParseException: Browser sent badly-formatted If-Modified-Since header: " + modifiedSinceStr);
            }
            catch (NumberFormatException e)
            {
                if (logger.isInfoEnabled())
                    logger.info("NumberFormatException: Browser sent badly-formatted If-Modified-Since header: " + modifiedSinceStr);
            }

            if (modifiedSince > 0L)
            {
                // round the date to the ignore millisecond value which is not supplied by header
                long modDate = (modified.getTime() / 1000L) * 1000L;
                if (modDate <= modifiedSince)
                {
                    res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
            }
        }

        // get the content reader
        ContentReader reader = contentService.getReader(nodeRef, propertyQName);
        if (reader == null || !reader.exists())
        {
            throw new WebScriptException(HttpServletResponse.SC_NOT_FOUND, "Unable to locate content for node ref " + nodeRef + " (property: " + propertyQName.toString() + ")");
        }
        
        // Stream the cotent
        streamContentImpl(req, res, reader, attach, modified, String.valueOf(modifiedSince));
    }

    /**
     * Streams content back to client from a given resource path.
     * 
     * @param req           The request
     * @param res           The response
     * @param resourcePath  The resource path the content is required for
     * @param attach        Indicates whether the content should be streamed as an attachment or not
     * @throws IOException
     */
    protected void streamContent(WebScriptRequest req, WebScriptResponse res, String resourcePath, boolean attach)
        throws IOException
    {
        // get extension of resource
        String ext = "";
        int extIndex = resourcePath.lastIndexOf('.');
        if (extIndex != -1)
        {
            ext = resourcePath.substring(extIndex);
        }
        
        // create temporary file 
        File file = TempFileProvider.createTempFile("streamContent-", ext);
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
        OutputStream os = new FileOutputStream(file);        
        FileCopyUtils.copy(is, os);        
        
        // stream the contents of the file
        streamContent(req, res, file, attach);
    }
    
    /**
     * Streams content back to client from a given resource path.
     * 
     * @param req           The request
     * @param res           The response
     * @param resourcePath  The resource path the content is required for
     * @param attach        Indicates whether the content should be streamed as an attachment or not
     * @throws IOException
     */
    protected void streamContent(WebScriptRequest req, WebScriptResponse res, File file, boolean attach)
        throws IOException
    {
        // determine mimetype from file extension
        String filePath = file.getAbsolutePath();
        String mimetype = MimetypeMap.MIMETYPE_BINARY;
        int extIndex = filePath.lastIndexOf('.');
        if (extIndex != -1)
        {
            String mt = mimetypeService.getMimetypesByExtension().get(filePath.substring(extIndex + 1));
            if (mt != null)
            {
                mimetype = mt;
            }
        }
        
        // Create a date in the past
        if (this.resouceFileModifiedDate == null)
        {
            Calendar calendar = Calendar.getInstance();
            calendar.set(1975, 3, 26);
            this.resouceFileModifiedDate = calendar.getTime();
        }      
        
        // setup file reader and stream
        FileContentReader reader = new FileContentReader(file);
        reader.setMimetype(mimetype);
        reader.setEncoding("UTF-8");
        
        streamContentImpl(req, res, reader, attach, this.resouceFileModifiedDate, String.valueOf(this.resouceFileModifiedDate.getTime()));
    }
    
    /**
     * Stream content implementation
     * 
     * @param req
     * @param res
     * @param reader
     * @param attach
     * @param modified
     * @throws IOException
     */
    protected void streamContentImpl(WebScriptRequest req, WebScriptResponse res, ContentReader reader, boolean attach, Date modified, String eTag)
        throws IOException
    {
        // handle attachment
        if (attach == true)
        {
            // set header based on filename - will force a Save As from the browse if it doesn't recognize it
            // this is better than the default response of the browser trying to display the contents
            res.setHeader("Content-Disposition", "attachment");
        }

        // establish mimetype
        String mimetype = reader.getMimetype();
        String extensionPath = req.getExtensionPath();
        if (mimetype == null || mimetype.length() == 0)
        {
            mimetype = MimetypeMap.MIMETYPE_BINARY;
            int extIndex = extensionPath.lastIndexOf('.');
            if (extIndex != -1)
            {
                String ext = extensionPath.substring(extIndex + 1);
                String mt = mimetypeService.getMimetypesByExtension().get(ext);
                if (mt != null)
                {
                    mimetype = mt;
                }
            }
        }

        // set mimetype for the content and the character encoding + length for the stream
        res.setContentType(mimetype);
        res.setContentEncoding(reader.getEncoding());
        res.setHeader("Content-Length", Long.toString(reader.getSize()));
        
        // set caching
        Cache cache = new Cache();
        cache.setNeverCache(false);
        cache.setMustRevalidate(true);
        cache.setLastModified(modified);
        cache.setETag(eTag);
        res.setCache(cache);
        
        // get the content and stream directly to the response output stream
        // assuming the repository is capable of streaming in chunks, this should allow large files
        // to be streamed directly to the browser response stream.
        try
        {
            reader.getContent(res.getOutputStream());
        }
        catch (SocketException e1)
        {
            // the client cut the connection - our mission was accomplished apart from a little error message
            if (logger.isInfoEnabled())
                logger.info("Client aborted stream read:\n\tcontent: " + reader);
        }
        catch (ContentIOException e2)
        {
            if (logger.isInfoEnabled())
                logger.info("Client aborted stream read:\n\tcontent: " + reader);
        }
    }
}
