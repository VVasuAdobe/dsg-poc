package com.adobe.aem.assets.poc.core.schedulers;


public interface AssetExpirationSchedulerConfig {


    boolean enabled();


    String scheduledTime();


    String rootPath();
}
