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
package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.keycloak.operator.crds.Keycloak;
import org.keycloak.operator.crds.KeycloakSpec;
import org.keycloak.operator.crds.KeycloakStatus;

import static org.keycloak.operator.crds.KeycloakStatus.State.*;

public class KeycloakDeployment {

    KubernetesClient client = null;

    KeycloakDeployment(KubernetesClient client) {
        this.client = client;
    }

    public Deployment getKeycloakDeployment(Keycloak keycloak) {
        // TODO this should be done through an informer to leverage caches
        // WORKAROUND for: https://github.com/java-operator-sdk/java-operator-sdk/issues/781
        return client
                .apps()
                .deployments()
                .inNamespace(keycloak.getMetadata().getNamespace())
                .list()
                .getItems()
                .stream()
                .filter((d) -> d.getMetadata().getName().equals(org.keycloak.operator.Constants.NAME))
                .findFirst()
                .orElse(null);
//                .withName(Constants.NAME)
//                .get();
    }

    public void createKeycloakDeployment(Keycloak keycloak) {
        client
            .apps()
            .deployments()
            .inNamespace(keycloak.getMetadata().getNamespace())
            .create(newKeycloakDeployment(keycloak));
    }

    public Deployment newKeycloakDeployment(Keycloak keycloak) {
        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(Constants.NAME)
                    .withNamespace(keycloak.getMetadata().getNamespace())
                    .addToLabels(Constants.MANAGED_BY_LABEL, Constants.MANAGED_BY_VALUE)
                    .addNewOwnerReference()
                        .withApiVersion(Constants.CRDS_VERSION)
                        .withKind(keycloak.getKind())
                        .withName(keycloak.getMetadata().getName())
                        .withUid(keycloak.getMetadata().getUid())
                    .endOwnerReference()
                .endMetadata()

                .withNewSpec()

                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(Constants.DEFAULT_LABELS)
                .endMetadata()
                .withNewSpec()

                .addNewInitContainer()
                .withName("init-container")
                .withImage(Constants.DEFAULT_KEYCLOAK_INIT_IMAGE)
                .endInitContainer()

                .addNewContainer()
                .withName(Constants.NAME)
                .withImage(Constants.DEFAULT_KEYCLOAK_IMAGE)
                .addNewPort()
                .withContainerPort(8443)
                .withProtocol("TCP")
                .endPort()
                .endContainer()
                .endSpec()

                .endTemplate()
                .withReplicas(keycloak.getSpec().getInstances())
                .withNewSelector()
                .addToMatchLabels(Constants.DEFAULT_LABELS)
                .endSelector()

                .endSpec()
                .build();
    }

    public KeycloakStatus getNextStatus(KeycloakSpec desired, KeycloakStatus prev, Deployment current) {

        var isReady = (current != null &&
                current.getStatus() != null &&
                current.getStatus().getReadyReplicas() != null);

        if (prev == null) {
            var newStatus = new KeycloakStatus();
            newStatus.setState(READY);
            newStatus.setMessage("Keycloak deployment started");
            return newStatus;
        }

        switch (prev.getState()) {
            case READY:
                if (isReady) {
                    return null;
                } else {
                    var newStatus = prev.clone();
                    newStatus.setState(UNKNOWN);
                    newStatus.setMessage("Keycloak deployment is NOT ready");
                    return newStatus;
                }
            case ERROR:
            case UNKNOWN:
                if (isReady) {
                    var newStatus = prev.clone();
                    newStatus.setState(READY);
                    newStatus.setMessage("Keycloak deployment is ready!");
                    return newStatus;
                } else {
                    return null;
                }
        }

        throw new RuntimeException("unreachable");
    }

}
