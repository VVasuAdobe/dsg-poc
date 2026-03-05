package com.adobe.aem.assets.poc.core.servlets;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.Servlet;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Servlet to search assets by offTime property using Eastern Time (ET) timezone.
 * 
 * Endpoint: /bin/search/assets-by-offtime
 * 
 * Parameters:
 * - datetime: ET datetime in format "yyyy-MM-dd HH:mm:ss" (required)
 * - operation: Query operation - "greater", "less", "greaterequals", "lessequals", "equals" (required)
 * - path: DAM path to search in (optional, defaults to /content/dam)
 * - limit: Maximum number of results (optional, defaults to -1 for all results)
 * 
 * Examples:
 * - /bin/search/assets-by-offtime?datetime=2024-03-15 14:30:00&operation=greater
 * - /bin/search/assets-by-offtime?datetime=2024-03-15 09:00:00&operation=greater&path=/content/dam/projects&limit=50
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/search/assets-by-offtime",
        "sling.servlet.methods=GET"
    }
)
public class AssetOffTimeSearchServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AssetOffTimeSearchServlet.class);
    
    private static final String PARAM_DATETIME = "datetime";
    private static final String PARAM_OPERATION = "operation";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_LIMIT = "limit";
    
    private static final String DEFAULT_PATH = "/content/dam";
    private static final String DEFAULT_LIMIT = "-1";
    
    private static final String ET_TIMEZONE = "America/New_York";
    private static final String UTC_TIMEZONE = "UTC";
    
    private static final String ET_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    @Reference
    private QueryBuilder queryBuilder;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) 
            throws IOException {
        
        LOG.info("AssetOffTimeSearchServlet invoked");
        
        // Get parameters
        String etDateTime = request.getParameter(PARAM_DATETIME);
        String operation = request.getParameter(PARAM_OPERATION);
        String path = request.getParameter(PARAM_PATH);
        String limit = request.getParameter(PARAM_LIMIT);
        
        // Validate required parameters
        if (etDateTime == null || etDateTime.trim().isEmpty()) {
            sendErrorResponse(response, 400, "Missing required parameter: datetime (format: yyyy-MM-dd HH:mm:ss)");
            return;
        }
        
        if (operation == null || operation.trim().isEmpty()) {
            sendErrorResponse(response, 400, "Missing required parameter: operation (values: greater, less, greaterequals, lessequals, equals)");
            return;
        }
        
        // Validate operation
        if (!isValidOperation(operation)) {
            sendErrorResponse(response, 400, "Invalid operation: " + operation + ". Valid values: greater, less, greaterequals, lessequals, equals");
            return;
        }
        
        // Set defaults
        if (path == null || path.trim().isEmpty()) {
            path = DEFAULT_PATH;
        }
        if (limit == null || limit.trim().isEmpty()) {
            limit = DEFAULT_LIMIT;
        }
        
        try {
            // Convert ET datetime to UTC
            String utcDateTime = convertETtoUTC(etDateTime);
            LOG.info("Converted ET datetime '{}' to UTC: '{}'", etDateTime, utcDateTime);
            
            // Build query predicates
            Map<String, String> predicates = buildQueryPredicates(path, utcDateTime, operation, limit);
            
            // Execute query
            Session session = request.getResourceResolver().adaptTo(Session.class);
            if (session == null) {
                sendErrorResponse(response, 500, "Unable to obtain JCR session");
                return;
            }
            
            Query query = queryBuilder.createQuery(PredicateGroup.create(predicates), session);
            SearchResult result = query.getResult();
            
            LOG.info("Query executed. Total matches: {}", result.getTotalMatches());
            
            // Build JSON response
            JsonObject jsonResponse = buildJsonResponse(result, etDateTime, utcDateTime, operation, path);
            
            // Send response
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(jsonResponse.toString());
            
        } catch (ParseException e) {
            LOG.error("Failed to parse datetime: {}", etDateTime, e);
            sendErrorResponse(response, 400, "Invalid datetime format. Expected: yyyy-MM-dd HH:mm:ss. Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error executing query", e);
            sendErrorResponse(response, 500, "Error executing query: " + e.getMessage());
        }
    }
    
    /**
     * Convert Eastern Time datetime to UTC ISO 8601 format
     */
    private String convertETtoUTC(String etDateTime) throws ParseException {
        // Parse the ET datetime
        SimpleDateFormat etFormat = new SimpleDateFormat(ET_DATE_FORMAT);
        etFormat.setTimeZone(TimeZone.getTimeZone(ET_TIMEZONE));
        
        Calendar etCalendar = Calendar.getInstance(TimeZone.getTimeZone(ET_TIMEZONE));
        etCalendar.setTime(etFormat.parse(etDateTime));
        
        // Convert to UTC format
        SimpleDateFormat utcFormat = new SimpleDateFormat(UTC_DATE_FORMAT);
        utcFormat.setTimeZone(TimeZone.getTimeZone(UTC_TIMEZONE));
        
        return utcFormat.format(etCalendar.getTime());
    }
    
    /**
     * Build query predicates map
     */
    private Map<String, String> buildQueryPredicates(String path, String utcDateTime, 
                                                      String operation, String limit) {
        Map<String, String> predicates = new HashMap<>();
        predicates.put("path", path);
        predicates.put("type", "dam:Asset");
        predicates.put("property", "jcr:content/offTime");
        predicates.put("property.operation", operation);
        predicates.put("property.value", utcDateTime);
        predicates.put("p.limit", limit);
        
        return predicates;
    }
    
    /**
     * Build JSON response from search results
     */
    private JsonObject buildJsonResponse(SearchResult result, String etDateTime, 
                                         String utcDateTime, String operation, String path) {
        JsonObject json = new JsonObject();
        json.addProperty("success", true);
        json.addProperty("total", result.getTotalMatches());
        json.addProperty("query_path", path);
        json.addProperty("query_operation", operation);
        json.addProperty("input_datetime_et", etDateTime);
        json.addProperty("converted_datetime_utc", utcDateTime);
        
        // Add timezone information
        JsonObject timezoneInfo = new JsonObject();
        TimeZone etTz = TimeZone.getTimeZone(ET_TIMEZONE);
        timezoneInfo.addProperty("input_timezone", ET_TIMEZONE);
        timezoneInfo.addProperty("input_timezone_display", etTz.getDisplayName());
        timezoneInfo.addProperty("storage_timezone", "UTC");
        json.add("timezone_info", timezoneInfo);
        
        // Build hits array
        JsonArray hits = new JsonArray();
        for (Hit hit : result.getHits()) {
            try {
                JsonObject hitObj = new JsonObject();
                hitObj.addProperty("path", hit.getPath());
                hitObj.addProperty("title", hit.getTitle());
                
                // You can add more properties here if needed
                // For example, to get the actual offTime value:
                // Resource resource = hit.getResource();
                // if (resource != null) {
                //     Resource jcrContent = resource.getChild("jcr:content");
                //     if (jcrContent != null) {
                //         ValueMap props = jcrContent.getValueMap();
                //         Calendar offTime = props.get("offTime", Calendar.class);
                //         if (offTime != null) {
                //             hitObj.addProperty("offTime", formatCalendar(offTime));
                //         }
                //     }
                // }
                
                hits.add(hitObj);
            } catch (RepositoryException e) {
                LOG.error("Error processing hit", e);
            }
        }
        json.add("hits", hits);
        
        return json;
    }
    
    /**
     * Validate query operation
     */
    private boolean isValidOperation(String operation) {
        return "greater".equals(operation) || 
               "less".equals(operation) || 
               "greaterequals".equals(operation) || 
               "lessequals".equals(operation) || 
               "equals".equals(operation);
    }
    
    /**
     * Send error response
     */
    private void sendErrorResponse(SlingHttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        error.addProperty("status", status);
        
        response.getWriter().write(error.toString());
    }
}
