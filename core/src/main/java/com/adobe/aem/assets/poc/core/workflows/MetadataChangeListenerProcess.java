package com.adobe.aem.assets.poc.core.workflows;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.Rendition;
import com.day.cq.dam.commons.util.DamUtil;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;


@Component(service = WorkflowProcess.class, property = { "process.label = POC - Metadata Change Listener" })
public class MetadataChangeListenerProcess implements WorkflowProcess {

	private static final Logger LOG = LoggerFactory.getLogger(MetadataChangeListenerProcess.class);
	private static final String API_SERVER_DOMAIN = "https://skateable-ivanna-vitreum.ngrok-free.dev/";
	private static final String API_URL = "api/uploadAsset";
	private static final String SERVICE_USER = "service-user";
	private static final String OUTPUT_FOLDER_PATH = "/content/dam/poc/output-folder";

	@Reference
	private ResourceResolverFactory resolverFactory;

	@Override
	public void execute(WorkItem item, WorkflowSession session, MetaDataMap args) throws WorkflowException {

		String payloadPath = item.getWorkflowData().getPayload().toString();
		LOG.error("Payload :{}",payloadPath);
		try (ResourceResolver resolver = resolverFactory
			.getServiceResourceResolver(Collections
				.<String, Object>singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_USER))) {
			Resource resource = resolver.getResource(payloadPath);
			if (null != resource){
			// Getting asset from the payload
			Asset asset =DamUtil.resolveToAsset(resource);
			String destinationAsset =OUTPUT_FOLDER_PATH +"/" + asset.getName();
				// check whether if asset already present in destination folder. If not, will proceed with creating new asset
				if(null == resolver.getResource(destinationAsset)){
					String apiResponse  = invokeHttpPost(asset);
					LOG.debug("Api Response : {}", apiResponse);
				}
			}
		} catch (LoginException e) {
			LOG.error("Exception: {}",e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private String invokeHttpPost(Asset asset){
		String apiResponse ="";
		try (CloseableHttpClient client = HttpClients.createDefault()) {

			String fileName = asset.getName();
			String jsonBody =
				"{\n" +
					"\"outputFolder\" : \""+OUTPUT_FOLDER_PATH+"\", "+
					"    \"assets\":[{\n" +
							"  \"fileName\": \""+fileName+"\",\n" +
							"  \"sourceUrl\": \""+asset.getPath()+"\",\n" +
							"  \"fileSize\": 3035\n" +
							"    }]\n" +
					"}";
			HttpPost post = new HttpPost(API_SERVER_DOMAIN + API_URL);
			post.setHeader("Content-Type", "application/json");
			post.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
			try (CloseableHttpResponse response = client.execute(post)) {
				String responseBody = "";
				if (response.getEntity() != null) {
					responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
				}
			}
		} catch (Exception e) {
			LOG.error("Error calling external API", e);
			apiResponse = e.getMessage() + ":::" + Arrays.toString(e.getStackTrace());
			throw new RuntimeException(e);
		}
		return apiResponse;
	}
}

