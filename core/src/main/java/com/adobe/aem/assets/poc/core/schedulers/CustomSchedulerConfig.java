package com.adobe.aem.assets.poc.core.schedulers;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration interface for Custom Scheduler
 */
@ObjectClassDefinition(
    name = "DSG POC - Custom Scheduler Configuration",
    description = "Configuration for custom scheduled task"
)
public @interface CustomSchedulerConfig {

    @AttributeDefinition(
        name = "Scheduler Name",
        description = "Name of the scheduler. Can be set via environment variable SCHEDULER_NAME",
        type = AttributeType.STRING
    )
    String schedulerName() default "$[env:SCHEDULER_NAME;default=Custom Scheduler]";

    @AttributeDefinition(
        name = "Enabled",
        description = "Enable or disable the scheduler. Can be set via environment variable SCHEDULER_ENABLED",
        type = AttributeType.BOOLEAN
    )
    boolean enabled() default false;

    @AttributeDefinition(
        name = "Hour",
        description = "Hour of the day (0-23) when the scheduler should run. Can be set via environment variable SCHEDULER_HOUR",
        type = AttributeType.INTEGER
    )
    int hour() default 9;

    @AttributeDefinition(
        name = "Minute",
        description = "Minute of the hour (0-59) when the scheduler should run. Can be set via environment variable SCHEDULER_MINUTE",
        type = AttributeType.INTEGER
    )
    int minute() default 0;

    @AttributeDefinition(
        name = "Time Zone",
        description = "Time zone for the scheduler (e.g., 'America/New_York', 'UTC'). Can be set via environment variable SCHEDULER_TIMEZONE",
        type = AttributeType.STRING
    )
    String timeZone() default "$[env:SCHEDULER_TIMEZONE;default=America/New_York]";
}
