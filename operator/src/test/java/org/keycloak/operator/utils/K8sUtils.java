/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.operator.utils;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.extended.run.RunConfigBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.logging.Log;
import org.awaitility.Awaitility;
import org.keycloak.operator.v2alpha1.crds.Keycloak;
import org.keycloak.operator.v2alpha1.crds.KeycloakStatusCondition;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Vaclav Muzikar <vmuzikar@redhat.com>
 */
public final class K8sUtils {
    public static <T> T getResourceFromFile(String fileName) {
        return Serialization.unmarshal(Objects.requireNonNull(K8sUtils.class.getResourceAsStream("/" + fileName)), Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    public static <T> T getResourceFromMultiResourceFile(String fileName, int index) {
        return ((List<T>) getResourceFromFile(fileName)).get(index);
    }

    public static Keycloak getDefaultKeycloakDeployment() {
        return getResourceFromMultiResourceFile("example-keycloak.yml", 0);
    }

    public static void deployKeycloak(KubernetesClient client, Keycloak kc, boolean waitUntilReady) {
        client.resources(Keycloak.class).createOrReplace(kc);

        if (waitUntilReady) {
            waitForKeycloakToBeReady(client, kc);
        }
    }

    public static void deployDefaultKeycloak(KubernetesClient client) {
        deployKeycloak(client, getDefaultKeycloakDeployment(), true);
    }

    public static void waitForKeycloakToBeReady(KubernetesClient client, Keycloak kc) {
        Log.infof("Waiting for Keycloak \"%s\"", kc.getMetadata().getName());
        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var currentKc = client.resources(Keycloak.class).withName(kc.getMetadata().getName()).get();
                    CRAssert.assertKeycloakStatusCondition(currentKc, KeycloakStatusCondition.READY, true);
                    CRAssert.assertKeycloakStatusCondition(currentKc, KeycloakStatusCondition.HAS_ERRORS, false);
                });
    }

    public static String inClusterCurl(KubernetesClient k8sclient, String namespace, String url) {
        return inClusterCurl(k8sclient, namespace, url, "-s", "-o", "/dev/null", "-w", "%{http_code}", url);
    }

    public static String inClusterCurl(KubernetesClient k8sclient, String namespace, String... args) {
        try {
            Pod curlPod = k8sclient.run().inNamespace(namespace)
                    .withRunConfig(new RunConfigBuilder()
                            .withArgs(args)
                            .withName("curl")
                            .withImage("curlimages/curl:7.78.0")
                            .withRestartPolicy("Never")
                            .build())
                    .done();
            Log.info("Waiting for curl Pod to finish running");
            Awaitility.await().atMost(2, MINUTES)
                    .until(() -> {
                        String phase =
                                k8sclient.pods().inNamespace(namespace).withName("curl").get()
                                        .getStatus().getPhase();
                        return phase.equals("Succeeded") || phase.equals("Failed");
                    });

            String curlOutput =
                    k8sclient.pods().inNamespace(namespace)
                            .withName(curlPod.getMetadata().getName()).getLog();

            return curlOutput;
        } catch (KubernetesClientException ex) {
            throw new AssertionError(ex);
        } finally {
            Log.info("Deleting curl Pod");
            k8sclient.pods().inNamespace(namespace).withName("curl").delete();
            Awaitility.await().atMost(1, MINUTES)
                    .until(() -> k8sclient.pods().inNamespace(namespace).withName("curl")
                            .get() == null);
        }
    }
}
