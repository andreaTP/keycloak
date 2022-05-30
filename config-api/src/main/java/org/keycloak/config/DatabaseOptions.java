package org.keycloak.config;

import org.keycloak.config.database.Database;

import java.util.ArrayList;
import java.util.List;

public class DatabaseOptions {

    // TODO: check that those properties are working even without description
    public final static Option dbDialect = new OptionBuilder<>("db-dialect", String.class)
            .category(OptionCategory.DATABASE)
            .runtimes(Option.Runtime.OPERATOR)
            .buildTime(true)
            .build();

    // TODO: check that those properties are working even without description
    public final static Option dbDriver = new OptionBuilder<>("db-driver", String.class)
            .category(OptionCategory.DATABASE)
            .runtimes(Option.Runtime.OPERATOR)
            .defaultValue(Database.getDriver("dev-file", true).get())
            .build();

    public final static Option db = new OptionBuilder<>("db", Database.Vendor.class)
            .category(OptionCategory.DATABASE)
            .description("The database vendor. Possible values are: " + String.join(", ", Database.getAliases()))
            .expectedValues(Database.Vendor.values()) // TODO: verify how this renders
            .buildTime(true)
            .build();

    public final static Option dbUrl = new OptionBuilder<>("db-url", String.class)
            .category(OptionCategory.DATABASE)
            .description("The full database JDBC URL. If not provided, a default URL is set based on the selected database vendor. " +
                    "For instance, if using 'postgres', the default JDBC URL would be 'jdbc:postgresql://localhost/keycloak'. ")
            .build();

    public final static Option dbUrlHost = new OptionBuilder<>("db-url-host", String.class)
            .category(OptionCategory.DATABASE)
            .description("Sets the hostname of the default JDBC URL of the chosen vendor. If the `db-url` option is set, this option is ignored.")
            .build();

    public final static Option dbUrlDatabase = new OptionBuilder<>("db-url-database", String.class)
            .category(OptionCategory.DATABASE)
            .description("Sets the database name of the default JDBC URL of the chosen vendor. If the `db-url` option is set, this option is ignored.")
            .build();

    public final static Option dbUrlPort = new OptionBuilder<>("db-url-port", Integer.class)
            .category(OptionCategory.DATABASE)
            .description("Sets the port of the default JDBC URL of the chosen vendor. If the `db-url` option is set, this option is ignored.")
            .build();

    public final static Option dbUrlProperties = new OptionBuilder<>("db-url-properties", String.class)
            .category(OptionCategory.DATABASE)
            .description("Sets the properties of the default JDBC URL of the chosen vendor. If the `db-url` option is set, this option is ignored.")
            .build();

    public final static Option dbUsername = new OptionBuilder<>("db-username", String.class)
            .category(OptionCategory.DATABASE)
            .description("The username of the database user.")
            .build();

    public final static Option dbPassword = new OptionBuilder<>("db-password", String.class)
            .category(OptionCategory.DATABASE)
            .description("The password of the database user.")
            .build();

    public final static Option dbSchema = new OptionBuilder<>("db-schema", String.class)
            .category(OptionCategory.DATABASE)
            .description("The database schema to be used.")
            .build();

    public final static Option dbPoolInitialSize = new OptionBuilder<>("db-pool-initial-size", Integer.class)
            .category(OptionCategory.DATABASE)
            .description("The initial size of the connection pool.")
            .build();

    public final static Option dbPoolMinSize = new OptionBuilder<>("db-pool-min-size", Integer.class)
            .category(OptionCategory.DATABASE)
            .description("The minimal size of the connection pool.")
            .build();

    public final static Option dbPoolMaxSize = new OptionBuilder<>("db-pool-max-size", Integer.class)
            .category(OptionCategory.DATABASE)
            .defaultValue(100)
            .description("The maximum size of the connection pool.")
            .build();

    public final static List<Option<?>> ALL_OPTIONS = new ArrayList<>();

    static {
        ALL_OPTIONS.add(dbDialect);
        ALL_OPTIONS.add(dbDriver);
        ALL_OPTIONS.add(db);
        ALL_OPTIONS.add(dbUrl);
        ALL_OPTIONS.add(dbUrlHost);
        ALL_OPTIONS.add(dbUrlDatabase);
        ALL_OPTIONS.add(dbUrlPort);
        ALL_OPTIONS.add(dbUrlProperties);
        ALL_OPTIONS.add(dbUsername);
        ALL_OPTIONS.add(dbPassword);
        ALL_OPTIONS.add(dbSchema);
        ALL_OPTIONS.add(dbPoolInitialSize);
        ALL_OPTIONS.add(dbPoolMinSize);
        ALL_OPTIONS.add(dbPoolMaxSize);
    }
}
