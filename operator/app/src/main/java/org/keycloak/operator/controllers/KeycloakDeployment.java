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
package org.keycloak.operator.controllers;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.logging.Log;
import org.keycloak.operator.Config;
import org.keycloak.operator.Constants;
import org.keycloak.operator.crds.v2alpha1.deployment.Keycloak;
import org.keycloak.operator.crds.v2alpha1.deployment.KeycloakStatusBuilder;

import java.util.Optional;

public class KeycloakDeployment extends AbstractKeycloak<Deployment> {

    public KeycloakDeployment(KubernetesClient client, Config config, Keycloak keycloakCR, Deployment existing, String adminSecretName) {
        super(client, config, keycloakCR, existing, adminSecretName);
    }

    @Override
    public Optional<HasMetadata> getReconciledResource() {
        Deployment baseDeployment = new DeploymentBuilder(this.base).build(); // clone not to change the base template
        Deployment reconciledDeployment;
        if (existing == null) {
            Log.info("No existing Deployment found, using the default");
            reconciledDeployment = baseDeployment;
        }
        else {
            Log.info("Existing Deployment found, updating specs");
            reconciledDeployment = new DeploymentBuilder(existing).build();

            // don't overwrite metadata, just specs
            reconciledDeployment.setSpec(baseDeployment.getSpec());

            // don't overwrite annotations in pod templates to support rolling restarts
            if (existing.getSpec() != null && existing.getSpec().getTemplate() != null) {
                mergeMaps(
                        Optional.ofNullable(reconciledDeployment.getSpec().getTemplate().getMetadata()).map(m -> m.getAnnotations()).orElse(null),
                        Optional.ofNullable(existing.getSpec().getTemplate().getMetadata()).map(m -> m.getAnnotations()).orElse(null),
                        annotations -> reconciledDeployment.getSpec().getTemplate().getMetadata().setAnnotations(annotations));
            }
        }

        return Optional.of(reconciledDeployment);
    }

    @Override
    protected Deployment fetchExisting() {
        return client
                .apps()
                .deployments()
                .inNamespace(getNamespace())
                .withName(getName())
                .get();
    }

    @Override
    protected Deployment createBase() {
        Deployment baseDeployment = new DeploymentBuilder()
                .withNewMetadata()
                .endMetadata()
                .withNewSpec()
                    .withNewSelector()
                        .addToMatchLabels("app", "")
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", "")
                        .endMetadata()
                        .withNewSpec()
                        .withRestartPolicy("Always")
                        .withTerminationGracePeriodSeconds(30L)
                        .withDnsPolicy("ClusterFirst")
                        .addNewContainer()
                            .withName("keycloak")
                            .withArgs("start")
                            .addNewPort()
                                .withContainerPort(8443)
                                .withProtocol("TCP")
                            .endPort()
                            .addNewPort()
                                .withContainerPort(8080)
                                .withProtocol("TCP")
                            .endPort()
                            .withNewReadinessProbe()
                                .withInitialDelaySeconds(20)
                                .withPeriodSeconds(2)
                                .withFailureThreshold(250)
                            .endReadinessProbe()
                            .withNewLivenessProbe()
                                .withInitialDelaySeconds(20)
                                .withPeriodSeconds(2)
                                .withFailureThreshold(150)
                            .endLivenessProbe()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .withNewStrategy()
                    .withNewRollingUpdate()
                        .withMaxSurge(new IntOrString("25%"))
                        .withMaxUnavailable(new IntOrString("25%"))
                    .endRollingUpdate()
                .endStrategy()
                .endSpec()
                .build();

        baseDeployment.getMetadata().setName(getName());
        baseDeployment.getMetadata().setNamespace(getNamespace());
        baseDeployment.getSpec().getSelector().setMatchLabels(Constants.DEFAULT_LABELS);
        baseDeployment.getSpec().setReplicas(keycloakCR.getSpec().getInstances());
        baseDeployment.getSpec().getTemplate().getMetadata().setLabels(Constants.DEFAULT_LABELS);

        Container container = baseDeployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        var customImage = Optional.ofNullable(keycloakCR.getSpec().getImage());
        container.setImage(customImage.orElse(config.keycloak().image()));
        if (customImage.isEmpty()) {
            container.getArgs().add("--auto-build");
        }

        container.setImagePullPolicy(config.keycloak().imagePullPolicy());

        container.setEnv(getEnvVars());

        configureHostname(container);
        configureTLS(baseDeployment.getSpec().getTemplate());
        mergePodTemplate(baseDeployment.getSpec().getTemplate());

        return baseDeployment;
    }


    public void updateStatus(KeycloakStatusBuilder status) {
        validatePodTemplate(status);
        if (existing == null) {
            status.addNotReadyMessage("No existing Deployment found, waiting for creating a new one");
            return;
        }

        var replicaFailure = existing.getStatus().getConditions().stream()
                .filter(d -> d.getType().equals("ReplicaFailure")).findFirst();
        if (replicaFailure.isPresent()) {
            status.addNotReadyMessage("Deployment failures");
            status.addErrorMessage("Deployment failure: " + replicaFailure.get());
            return;
        }

        if (existing.getStatus() == null
                || existing.getStatus().getReadyReplicas() == null
                || existing.getStatus().getReadyReplicas() < keycloakCR.getSpec().getInstances()) {
            status.addNotReadyMessage("Waiting for more replicas");
        }

        var progressing = existing.getStatus().getConditions().stream()
                .filter(c -> c.getType().equals("Progressing")).findFirst();
        progressing.ifPresent(p -> {
            String reason = p.getReason();
            // https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#progressing-deployment
            if (p.getStatus().equals("True") &&
                    (reason.equals("NewReplicaSetCreated") || reason.equals("FoundNewReplicaSet") || reason.equals("ReplicaSetUpdated"))) {
                status.addRollingUpdateMessage("Rolling out deployment update");
            }
        });
    }

    @Override
    public void rollingRestart() {
        client.apps().deployments()
                .inNamespace(getNamespace())
                .withName(getName())
                .rolling().restart();
    }
}
