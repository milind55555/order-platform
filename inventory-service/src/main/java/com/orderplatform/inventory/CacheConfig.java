package com.orderplatform.inventory;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Kept separate from the application class so web-slice tests do not require a CacheManager. */
@Configuration
@EnableCaching
public class CacheConfig {
}
