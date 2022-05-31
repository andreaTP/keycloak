package org.keycloak.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class LoggingOptions {

    public static final Handler DEFAULT_LOG_HANDLER = Handler.console;
    public static final Level DEFAULT_LOG_LEVEL = Level.INFO;
    public static final Output DEFAULT_CONSOLE_OUTPUT = Output.DEFAULT;
    public static final String DEFAULT_LOG_FILENAME = "keycloak.log";
    public static final String DEFAULT_LOG_PATH = "data" + File.separator + "log" + File.separator + DEFAULT_LOG_FILENAME;

    public enum Handler {
        console,
        file,
        // TODO: find an encoding for the comma and possibly use it for all enums
        console_file,
        file_console
    }

    public final static Option log = new OptionBuilder<>("log", Handler.class)
            .category(OptionCategory.LOGGING)
            .description("Enable one or more log handlers in a comma-separated list. Available log handlers are: " + Arrays.stream(Handler.values()).map(h -> h.toString()).collect(Collectors.joining(", ")))
            .defaultValue(DEFAULT_LOG_HANDLER)
            .expectedValues(Handler.values())
            .build();

    // TODO: improve the enums encoding!
    public enum Level {
        OFF,
        FATAL,
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE,
        ALL;
    }

    public final static Option logLevel = new OptionBuilder<>("log-level", Level.class)
            .category(OptionCategory.LOGGING)
            .defaultValue(DEFAULT_LOG_LEVEL)
            .description("The log level of the root category or a comma-separated list of individual categories and their levels. For the root category, you don't need to specify a category.")
            .build();

    public enum Output {
        DEFAULT, // TODO: map this to lowercase!
        JSON
    }
    public final static Option logConsoleOutput = new OptionBuilder<>("log-console-output", Output.class)
            .category(OptionCategory.LOGGING)
            .defaultValue(DEFAULT_CONSOLE_OUTPUT)
            .description("Set the log output to JSON or default (plain) unstructured logging.")
            .expectedValues(Output.values())
            .build();

    public final static Option logConsoleFormat = new OptionBuilder<>("log-console-format", String.class)
            .category(OptionCategory.LOGGING)
            .description("The format of unstructured console log entries. If the format has spaces in it, escape the value using \"<format>\".")
            .defaultValue("%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n")
            .build();

    public final static Option logConsoleColor = new OptionBuilder<>("log-console-color", Boolean.class)
            .category(OptionCategory.LOGGING)
            .description("Enable or disable colors when logging to console.")
            .defaultValue(Boolean.FALSE) // :-(
            .build();

    public final static Option logConsoleEnabled = new OptionBuilder<>("log-console-enabled", Boolean.class)
            .category(OptionCategory.LOGGING) // TODO: check that params without description are working
            .runtimes(Collections.emptySet()) // TODO: verify
            .build();

    public final static Option logFileEnabled = new OptionBuilder<>("log-file-enabled", Boolean.class)
            .category(OptionCategory.LOGGING) // TODO: check that params without description are working
            .runtimes(Collections.emptySet()) // TODO: verify
            .build();

    public final static Option logFile = new OptionBuilder<>("log-file", String.class)
            .description("Set the log file path and filename.")
            .defaultValue(DEFAULT_LOG_PATH)
            .build();

    public final static Option logFileFormat = new OptionBuilder<>("log-file-format", String.class)
            .description("Set a format specific to file log entries.")
            .defaultValue("%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n")
            .build();

    public final static List<Option<?>> ALL_OPTIONS = new ArrayList<>();

    static {
        ALL_OPTIONS.add(log);
        ALL_OPTIONS.add(logLevel);
        ALL_OPTIONS.add(logConsoleOutput);
        ALL_OPTIONS.add(logConsoleFormat);
        ALL_OPTIONS.add(logConsoleColor);
        ALL_OPTIONS.add(logConsoleEnabled);
        ALL_OPTIONS.add(logFileEnabled);
        ALL_OPTIONS.add(logFile);
        ALL_OPTIONS.add(logFileFormat);
    }
}
