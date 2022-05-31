package org.keycloak.config;

import java.util.ArrayList;
import java.util.List;

public class AllOptions {

    public final static List<Option<?>> ALL_OPTIONS = new ArrayList<>();

    static {
        ALL_OPTIONS.addAll(ClusteringOptions.ALL_OPTIONS);
        ALL_OPTIONS.addAll(DatabaseOptions.ALL_OPTIONS);
        ALL_OPTIONS.addAll(FeatureOptions.ALL_OPTIONS);
        ALL_OPTIONS.addAll(HealthOptions.ALL_OPTIONS);
        ALL_OPTIONS.addAll(HttpOptions.ALL_OPTIONS);
    }
}
