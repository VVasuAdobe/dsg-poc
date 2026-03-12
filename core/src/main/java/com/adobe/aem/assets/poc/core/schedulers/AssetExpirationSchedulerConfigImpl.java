package com.adobe.aem.assets.poc.core.schedulers;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration interface for Asset Expiration Scheduler
 */

@Component(
	service = AssetExpirationSchedulerConfig.class,
	configurationPolicy = ConfigurationPolicy.REQUIRE,
	immediate = true)
@Designate(ocd = AssetExpirationSchedulerConfiguration.class)
public class AssetExpirationSchedulerConfigImpl implements AssetExpirationSchedulerConfig {


	private boolean enabled;
	private String scheduledTime;
	private String rootPath;

	private static final Logger LOGGER = LoggerFactory.getLogger(AssetExpirationSchedulerConfigImpl.class);
	/**
	 * Activate.
	 *
	 * @param config the config
	 */
	@Activate
	@Modified
	protected void activate(final AssetExpirationSchedulerConfiguration config) {

		enabled = config.enabled();
		scheduledTime = config.scheduledTime();
		rootPath = config.rootPath();
		LOGGER.info(
			"Asset Expiration Scheduler Activated: Scheduled time: {}, Root path: {}",scheduledTime , rootPath);
	}

	@Override
	public boolean enabled() {
		return enabled;
	}

	@Override
	public String scheduledTime() {
		return scheduledTime;
	}

	@Override
	public String rootPath() {
		return rootPath;
	}

}
