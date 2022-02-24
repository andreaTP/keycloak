/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quarkus.runtime.cli.command;

import static org.keycloak.exportimport.ExportImportConfig.ACTION_IMPORT;
import static org.keycloak.exportimport.Strategy.IGNORE_EXISTING;
import static org.keycloak.exportimport.Strategy.OVERWRITE_EXISTING;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "import",
        header = "Import data from a directory or a file.",
        description = "%nImport data from a directory or a file.")
public final class Import extends AbstractExportImportCommand implements Runnable {

    @Option(names = "--dir",
            arity = "1",
            description = "Set the path to a directory from where files will be imported.",
            paramLabel = "<path>")
    String toDir;

    @Option(names = "--file",
            arity = "1",
            description = "Set the path to a file that will be used to import data.",
            paramLabel = "<path>")
    String toFile;


    @Option(names = "--realm",
            arity = "1",
            description = "Set the name of the realm to import",
            paramLabel = "<realm>")
    String realm;

    @Option(names = "--override",
            arity = "1",
            description = "Set if existing data should be skipped or overridden.",
            paramLabel = "false",
            defaultValue = "true")
    boolean override;

    public Import() {
        super(ACTION_IMPORT);
    }

    protected String getToDir() {
        return toDir;
    }
    protected String getToFile() {
        return toFile;
    }
    protected String getRealm() {
        return realm;
    }

    @Override
    protected void doBeforeRun() {
        System.setProperty("keycloak.migration.strategy", override ? OVERWRITE_EXISTING.name() : IGNORE_EXISTING.name());
    }
}
