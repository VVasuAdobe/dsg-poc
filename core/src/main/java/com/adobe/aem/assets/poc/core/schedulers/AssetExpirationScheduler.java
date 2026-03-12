package com.adobe.aem.assets.poc.core.schedulers;


import org.apache.commons.lang3.StringUtils;
import org.apache.sling.discovery.TopologyEvent;
import org.apache.sling.discovery.TopologyEventListener;
import org.apache.sling.event.jobs.JobBuilder;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;




/**
 * Custom Scheduler implementation for DSG Asset Expiration.
 * <p>
 * This scheduler accepts hour and minute in a configured timezone (e.g., America/New_York)
 * and converts to UTC for scheduling since AEM server runs in UTC. It triggers a job
 * to update asset expiration status at the configured time.
 * </p>
 * <p>
 * This scheduler is configured to run only on the leader instance in a clustered environment
 * to prevent duplicate job executions using TopologyEventListener.
 * </p>
 */
@Component(
	service = TopologyEventListener.class,
	immediate = true
)
public class AssetExpirationScheduler implements TopologyEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetExpirationScheduler.class);

    @Reference
    private JobManager jobManager;



	@Reference(
		cardinality = ReferenceCardinality.OPTIONAL,
		policy = ReferencePolicy.DYNAMIC,
		policyOption = ReferencePolicyOption.GREEDY
	)
	private volatile AssetExpirationSchedulerConfig config;

	private volatile boolean isLeader = false;

	@Reference(
		unbind = "unbindConfig",
		cardinality = ReferenceCardinality.OPTIONAL,
		policy = ReferencePolicy.DYNAMIC,
		updated = "updateConfig"
	)
	protected void bindConfig(AssetExpirationSchedulerConfig config) {
		this.config = config;
		LOGGER.info("AssetExpirationScheduler: Configuration bound");
		scheduleJobIfLeader();
	}

	protected void updateConfig(AssetExpirationSchedulerConfig config) {
		this.config = config;
		LOGGER.info("AssetExpirationScheduler: Configuration updated");
		scheduleJobIfLeader();
	}

	protected void unbindConfig(AssetExpirationSchedulerConfig config) {
		LOGGER.info("AssetExpirationScheduler: Configuration unbound");
		removeScheduler();
		this.config = null;
	}

	@Override
	public void handleTopologyEvent(TopologyEvent event) {
		if (event.getType() == TopologyEvent.Type.TOPOLOGY_CHANGED ||
			event.getType() == TopologyEvent.Type.TOPOLOGY_INIT) {

			boolean wasLeader = isLeader;
			isLeader = event.getNewView().getLocalInstance().isLeader();

			LOGGER.debug("AssetExpirationScheduler: Topology event - Type: {}, isLeader: {}",
				event.getType(), isLeader);

			if (isLeader && !wasLeader) {
				// Became leader, schedule job
				LOGGER.info("AssetExpirationScheduler: Instance became leader, scheduling job");
				scheduleJobIfLeader();
			} else if (!isLeader && wasLeader) {
				// Lost leadership, remove scheduler
				LOGGER.info("AssetExpirationScheduler: Instance lost leadership, removing scheduler");
				removeScheduler();
			}
		}
	}

	/**
	 * Schedule job if this instance is the leader and configuration is enabled
	 */
	private void scheduleJobIfLeader() {
		// Removing existing scheduler
		removeScheduler();
		if (isLeader && config != null && config.enabled()) {
			startScheduledJob();
		}  else {
			LOGGER.debug("AssetExpirationScheduler: Scheduler is not enabled");
		}
	}

	/**
	 * Remove a scheduler based on the job topic
	 */
	private void removeScheduler() {
		Collection<ScheduledJobInfo> scheduledJobInfos = jobManager.getScheduledJobs("test-job", 0);
		for (ScheduledJobInfo scheduledJobInfo : scheduledJobInfos) {
			LOGGER.debug("AssetExpirationScheduler: Unscheduling Job {}", scheduledJobInfo.getJobProperties());
			scheduledJobInfo.unschedule();
		}
	}

	/**
	 * Start a scheduler based on the job topic
	 */
	private void startScheduledJob() {
		if (config == null) {
			LOGGER.warn("AssetExpirationScheduler: Cannot start scheduled job - configuration is null");
			return;
		}

		LOGGER.debug("AssetExpirationScheduler: Asset Expiration scheduler :{} with Time {} and path {}",
			"Scheduler", config.scheduledTime(), config.rootPath());

		if (StringUtils.isNotEmpty(config.rootPath())) {

			Map<String, Object> propertiesMap = new HashMap<>();
			propertiesMap.put("asset-path", config.rootPath());


			JobBuilder.ScheduleBuilder scheduleBuilder = jobManager
				.createJob("test-job").properties(propertiesMap).schedule();
			scheduleBuilder.cron("");
			ScheduledJobInfo scheduledJobInfo = scheduleBuilder.add();

			if (scheduledJobInfo == null) {
				LOGGER.error("AssetExpirationScheduler: Failed to add Scheduled Job {}", "test-job");
			}
		}
	}

}
