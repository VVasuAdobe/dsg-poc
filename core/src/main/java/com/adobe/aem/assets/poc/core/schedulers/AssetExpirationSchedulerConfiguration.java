package com.adobe.aem.assets.poc.core.schedulers;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration interface for Asset Expiration Scheduler
 */
@ObjectClassDefinition(
    name = "DSG - Asset Expiration Scheduler Config",
    description = "Configuration for asset expiration scheduler task"
)
public @interface AssetExpirationSchedulerConfiguration {


    @AttributeDefinition(
        name = "Enabled",
        description = "Enable or disable the scheduler.",
        type = AttributeType.BOOLEAN
    )
    boolean enabled() default false;

		@AttributeDefinition(
			name = "Scheduler Time",
			description = "Time",
			type = AttributeType.STRING
		)
		String scheduledTime() default "12:00 AM ET";

    @AttributeDefinition(
        name = "Root Path",
        description = "Root Path for the scheduler run (e.g., '/content/dam/assets').",
        type = AttributeType.STRING
    )
    String rootPath() default "/content/dam/assets";
}


