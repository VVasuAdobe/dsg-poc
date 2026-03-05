package com.adobe.aem.assets.poc.core.workflows;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;
import java.text.SimpleDateFormat;


@Component(service = WorkflowProcess.class, property = { "process.label = POC - Time Update Workflow" })
public class TimeUpdateWorkflowProcess implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(TimeUpdateWorkflowProcess.class);
    private static final String SERVICE_USER = "dsg-service-user";
    private static final String ON_TIME_PROPERTY = "onTime";
    private static final String OFF_TIME_PROPERTY = "offTime";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {

        String payloadPath = item.getWorkflowData().getPayload().toString();
        LOG.info("Processing payload: {}", payloadPath);

        ResourceResolver resolver = null;
        try {
            // Use the workflow session to get admin resource resolver
            javax.jcr.Session jcrSession = session.adaptTo(javax.jcr.Session.class);
            if (jcrSession == null) {
                throw new WorkflowException("Could not adapt workflow session to JCR session");
            }
            
            resolver = resolverFactory.getResourceResolver(Collections.<String, Object>singletonMap("user.jcr.session", jcrSession));
            LOG.info("Using workflow session user: {}", resolver.getUserID());
            
            Resource resource = resolver.getResource(payloadPath);
            
            if (resource != null) {
                // Getting asset from the payload
                Asset asset = DamUtil.resolveToAsset(resource);
                
                if (asset != null) {
                    // Get the jcr:content resource of the asset
                    Resource jcrContentResource = resolver.getResource(payloadPath + "/jcr:content");
                    
                    if (jcrContentResource != null) {
                        // Get current time
                        Calendar currentTime = Calendar.getInstance();
                        
                        // Get the modifiable value map to update metadata
                        ModifiableValueMap properties = jcrContentResource.adaptTo(ModifiableValueMap.class);
                        
                        if (properties != null) {
                            // Set the onTime property with current time
                            properties.put(ON_TIME_PROPERTY, currentTime);
                            
                            // Read and log the offTime property
                            Calendar offTime = properties.get(OFF_TIME_PROPERTY, Calendar.class);
                            
                            if (offTime != null) {
                                // Log the offTime in its original timezone
                                TimeZone originalTimeZone = offTime.getTimeZone();
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                                sdf.setTimeZone(originalTimeZone);
                                String offTimeOriginal = sdf.format(offTime.getTime());
                                
                                LOG.info("offTime property found for asset: {} with value: {} (TimeZone: {})", 
                                        asset.getPath(), offTimeOriginal, originalTimeZone.getID());
                                
                                // Convert to UTC (Z zone) and log
                                SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                                utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                                String offTimeUTC = utcFormat.format(offTime.getTime());
                                
                                LOG.info("offTime in UTC (Z zone) for asset: {} is: {}", 
                                        asset.getPath(), offTimeUTC);
                                
                                // Update the offTime property to UTC in the node
                                Calendar offTimeUTC_Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                                offTimeUTC_Calendar.setTimeInMillis(offTime.getTimeInMillis());
                                properties.put(OFF_TIME_PROPERTY, offTimeUTC_Calendar);
                                
                                LOG.info("Updated offTime property to UTC for asset: {}", asset.getPath());
                            } else {
                                LOG.info("offTime property not found for asset: {}", asset.getPath());
                            }
                            
                            // Persist the changes
                            resolver.commit();
                            
                            LOG.info("Successfully updated onTime metadata for asset: {} with time: {}", 
                                    asset.getPath(), currentTime.getTime());
                        } else {
                            LOG.error("Could not adapt jcr:content to ModifiableValueMap for: {}", payloadPath);
                        }
                    } else {
                        LOG.error("jcr:content resource not found for: {}", payloadPath);
                    }
                } else {
                    LOG.error("Could not resolve resource to Asset: {}", payloadPath);
                }
            } else {
                LOG.error("Resource not found at payload path: {}", payloadPath);
            }
        } catch (org.apache.sling.api.resource.LoginException e) {
            LOG.error("Login exception while getting resource resolver: {}", e.getMessage(), e);
            throw new WorkflowException("Failed to get resource resolver", e);
        } catch (PersistenceException e) {
            LOG.error("Persistence exception while committing changes: {}", e.getMessage(), e);
            throw new WorkflowException("Failed to persist metadata changes", e);
        } finally {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }
}
