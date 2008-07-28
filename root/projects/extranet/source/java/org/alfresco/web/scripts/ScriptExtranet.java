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
package org.alfresco.web.scripts;

import java.rmi.RemoteException;
import java.util.ArrayList;

import org.alfresco.extranet.ExtranetHelper;
import org.alfresco.extranet.UserService;
import org.alfresco.extranet.database.DatabaseUser;
import org.alfresco.extranet.jira.JIRAClient;
import org.alfresco.extranet.jira.JIRAService;
import org.alfresco.web.framework.cache.BasicCache;
import org.alfresco.web.framework.cache.ContentCache;
import org.alfresco.web.site.HttpRequestContext;
import org.alfresco.web.site.RequestContext;

import com.dolby.jira.net.soap.jira.RemoteIssue;

/**
 * @author muzquiano
 */
public final class ScriptExtranet extends ScriptBase
{
    private static final String WEBSCRIPTS_REGISTRY = "webframework.webscripts.registry";
    
    public ScriptExtranet(RequestContext context)
    {
        super(context);
    }
    
    // no properties
    public ScriptableMap buildProperties()
    {
        return null;
    }
    
    public JIRAClient getJIRAClient()
    {
        JIRAClient jiraClient = null;
        
        JIRAService jiraService = ExtranetHelper.getJIRAService(((HttpRequestContext)context).getRequest());
        if(jiraService != null)
        {
            jiraClient = jiraService.getClient();
        }
        
        return jiraClient;
    }
    
    public static ContentCache checkInsCache = null;
    
    public synchronized Object[] getCheckIns(String filterId, int start, int end)
    {
        if(checkInsCache == null)
        {
            checkInsCache = new BasicCache(1000*60*5);  // five minutes
        }
        
        // cache key
        String key = filterId;
        
        // check to see if we already have this in the cache        
        RemoteIssue[] issues = (RemoteIssue[]) checkInsCache.get(key);
        if(issues == null)
        {
            String token = getJIRAClient().getToken();
            try
            {
                issues = getJIRAClient().getJiraSOAPService().getIssuesFromFilter(token, filterId);
                checkInsCache.put(key, issues);                
            }
            catch(RemoteException re)
            {
                re.printStackTrace();
            }
        }
        
        RemoteIssue[] array = null;
        if(issues != null)
        {        
            ArrayList arrayList = new ArrayList();
            for(int i = 0; i < (end - start); i++)
            {
                if(issues.length > start + i)
                {
                    arrayList.add(issues[start+i]);
                }
            }
            
            array = (RemoteIssue[]) arrayList.toArray(new RemoteIssue[arrayList.size()]);
        }
        
        return array;
    }
    
    /**
     * Resets the user's password and sends an email
     * 
     * @param userIdentity Either the user's id or their email address
     * @return true if successful
     */
    public boolean resetUserPassword(String userIdentity)
    {
        boolean reset = false;
        
        UserService userService = ExtranetHelper.getUserService(((HttpRequestContext)context).getRequest());
        
        DatabaseUser user = null;
        try
        {
            user = userService.getUser(userIdentity);
        }
        catch(Exception ex) { }
        if(user == null)
        {
            try
            {
                user = userService.getUserByEmail(userIdentity);
            }
            catch(Exception ex) { }
        }
        if(user != null)
        {
            userService.resetUserPassword(user.getUserId());
            reset = true;
        }
        
        return reset;
    }
    
    /**
     * Update user.
     * 
     * @param userId the user id
     * @param newUserId the new user id
     * @param email the email
     * @param firstName the first name
     * @param lastName the last name
     * 
     * @return true, if successful
     */
    public boolean setUserProperties(String originalUserId, String newUserId, String email, String firstName, String lastName)
        throws Exception
    {
        UserService userService = ExtranetHelper.getUserService(((HttpRequestContext)context).getRequest());
        
        // load the original user
        DatabaseUser user = userService.getUser(originalUserId);

        // set our new properties
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        
        return userService.setUserProperties(originalUserId, user); 
    }
    
    public boolean syncWebHelpdeskUser(String userId, String whdUserId)
        throws Exception
    {
        UserService userService = ExtranetHelper.getUserService(((HttpRequestContext)context).getRequest());
        
        return userService.syncWebHelpdeskUser(userId, whdUserId);
    }    
    
    public boolean changePassword(String userId, String originalPassword, String newPassword1, String newPassword2)
        throws Exception
    {
        UserService userService = ExtranetHelper.getUserService(((HttpRequestContext)context).getRequest());
        
        return userService.changePassword(userId, originalPassword, newPassword1, newPassword2);        
    }
}
