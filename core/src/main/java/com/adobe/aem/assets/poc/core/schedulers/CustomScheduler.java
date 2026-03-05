package com.adobe.aem.assets.poc.core.schedulers;

import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Custom Scheduler implementation for DSG POC
 * Accepts hour and minute in a configured timezone (e.g., America/New_York)
 * and converts to UTC for scheduling since AEM server runs in UTC
 */
@Component(immediate = true, service = Runnable.class)
@Designate(ocd = CustomSchedulerConfig.class)
public class CustomScheduler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CustomScheduler.class);
    
    @Reference
    private Scheduler scheduler;

    private String schedulerName;
    private String timeZone;
    private int hour;
    private int minute;
    private boolean enabled;

    @Activate
    protected void activate(CustomSchedulerConfig config) {
        this.schedulerName = config.schedulerName();
        this.timeZone = config.timeZone();
        this.hour = config.hour();
        this.minute = config.minute();
        this.enabled = config.enabled();

        LOG.info("Scheduler Configuration - Name: {}, Enabled: {}, Hour: {}, Minute: {}, TimeZone: {}", 
                schedulerName, enabled, hour, minute, timeZone);

        if (enabled) {
            scheduleJob();
        } else {
            LOG.info("Scheduler '{}' is disabled", schedulerName);
        }
    }

    @Deactivate
    protected void deactivate() {
        scheduler.unschedule(schedulerName);
        LOG.info("Scheduler '{}' deactivated", schedulerName);
    }

    /**
     * Schedule the job by converting configured time from source timezone to UTC
     */
    private void scheduleJob() {
        try {
            // Validate inputs
            if (hour < 0 || hour > 23) {
                LOG.error("Invalid hour value: {}. Must be between 0 and 23", hour);
                return;
            }
            if (minute < 0 || minute > 59) {
                LOG.error("Invalid minute value: {}. Must be between 0 and 59", minute);
                return;
            }

            // Convert time from configured timezone to UTC
            ZoneId zoneId = ZoneId.of(timeZone);
            ZonedDateTime localTime = ZonedDateTime.now(zoneId)
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(0);
            ZonedDateTime utcTime = localTime.withZoneSameInstant(ZoneId.of("UTC"));

            // Build cron expression for UTC time
            String cronExpression = String.format("0 %d %d * * ?", utcTime.getMinute(), utcTime.getHour());

            LOG.info("Configured time: {}: in timezone '{}'", hour, minute, timeZone);
            LOG.info("Converted to UTC: {}:", utcTime.getHour(), utcTime.getMinute());
            LOG.info("Cron expression: {}", cronExpression);

            // Schedule the job
            ScheduleOptions options = scheduler.EXPR(cronExpression);
            options.name(schedulerName);
            options.canRunConcurrently(false);
            scheduler.schedule(this, options);

            LOG.info("Scheduler '{}' activated successfully", schedulerName);
        } catch (Exception e) {
            LOG.error("Error scheduling job: {}", e.getMessage(), e);
        }
    }

    @Override
    public void run() {
        LOG.info("=== Executing scheduled job: {} ===", schedulerName);
        LOG.info("=== Scheduler triggered: {} ===", schedulerName);
       
        LOG.info("=== Scheduled job completed: {} ===", schedulerName);
    }

   
}
