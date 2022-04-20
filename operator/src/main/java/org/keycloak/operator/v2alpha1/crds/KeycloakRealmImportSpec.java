/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.operator.v2alpha1.crds;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.crd.generator.annotation.SchemaSwap;
import io.fabric8.crd.generator.annotation.SchemaSwaps;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.keycloak.representations.overrides.ComponentExportRepresentationMap;
import org.keycloak.representations.overrides.MultivaluedStringStringHashMap;
import org.keycloak.representations.overrides.NoSubGroupsGroupRepresentationList;
import org.keycloak.representations.overrides.NoSubcomponentsComponentExportRepresentationMap;

import javax.validation.constraints.NotNull;

//@SchemaSwap(originalType = GroupRepresentation.class, fieldName = "subGroups", targetType = NoSubGroupsGroupRepresentationList.class)
//@SchemaSwap(originalType = RealmRepresentation.class, fieldName = "components", targetType = ComponentExportRepresentationMap.class)
//@SchemaSwap(originalType = CredentialRepresentation.class, fieldName = "config", targetType = MultivaluedStringStringHashMap.class)
//@SchemaSwap(originalType = ComponentRepresentation.class, fieldName = "config", targetType = MultivaluedStringStringHashMap.class)
//@SchemaSwap(originalType = ComponentExportRepresentation.class, fieldName = "subComponents", targetType = NoSubcomponentsComponentExportRepresentationMap.class)
//@SchemaSwap(originalType = ComponentExportRepresentation.class, fieldName = "config", targetType = MultivaluedStringStringHashMap.class)
//@SchemaSwap(originalType = ScopeRepresentation.class, fieldName = "policies", targetType = void.class)
//@SchemaSwap(originalType = ScopeRepresentation.class, fieldName = "resources", targetType = void.class)
public class KeycloakRealmImportSpec {

    @NotNull
    @JsonPropertyDescription("The name of the Keycloak CR to reference, in the same namespace.")
    private String keycloakCRName;
    @NotNull
    @JsonPropertyDescription("The RealmRepresentation to import into Keycloak.")
    private RealmRepresentation realm;

    public String getKeycloakCRName() {
        return keycloakCRName;
    }

    public void setKeycloakCRName(String keycloakCRName) {
        this.keycloakCRName = keycloakCRName;
    }

    public RealmRepresentation getRealm() {
        return realm;
    }

    public void setRealm(RealmRepresentation realm) {
        this.realm = realm;
    }

}
