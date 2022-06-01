package org.keycloak.quarkus.runtime.configuration.mappers;

import org.keycloak.config.MetricsOptions;
import org.keycloak.config.OptionCategory;

import java.util.Arrays;

import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper.fromOption;


final class MetricsPropertyMappers {

    private MetricsPropertyMappers(){}

    public static PropertyMapper[] getMetricsPropertyMappers() {
        return new PropertyMapper[] {
                fromOption(MetricsOptions.metricsEnabled)
                        .to("quarkus.datasource.metrics.enabled")
                        .paramLabel(Boolean.TRUE + "|" + Boolean.FALSE)
                        .build()
        };
    }
}
