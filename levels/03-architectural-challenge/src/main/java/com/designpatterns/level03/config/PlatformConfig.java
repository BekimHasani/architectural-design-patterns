package com.designpatterns.level03.config;

import com.designpatterns.level03.service.routing.RoutingService;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** COMPOSITION ROOT for what component scanning doesn't create by itself. */
@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
public class PlatformConfig {

    @Bean
    RoutingService routingService(RoutingProperties routing) {
        return new RoutingService(routing.defaultOrder(), routing.preferredByCurrency());
    }
}
