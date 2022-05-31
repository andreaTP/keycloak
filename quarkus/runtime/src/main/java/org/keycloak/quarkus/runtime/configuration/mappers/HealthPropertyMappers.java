package org.keycloak.quarkus.runtime.configuration.mappers;

import org.keycloak.config.HealthOptions;
import org.keycloak.config.OptionCategory;

import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper.fromOption;


final class HealthPropertyMappers {

    private HealthPropertyMappers(){}

    public static PropertyMapper[] getHealthPropertyMappers() {
        return new PropertyMapper[] {
                fromOption(HealthOptions.httpEnabled)
                        .to("quarkus.datasource.health.enabled")
                        .paramLabel(Boolean.TRUE + "|" + Boolean.FALSE)
                        .build()
        };
    }

    private static PropertyMapper.Builder builder() {
        return PropertyMapper.builder(OptionCategory.HEALTH);
    }
}
