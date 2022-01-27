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
package org.keycloak.operator.v2alpha1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.quarkus.logging.Log;
import org.keycloak.operator.v2alpha1.crds.*;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;
import static org.keycloak.operator.Constants.*;

@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE, finalizerName = NO_FINALIZER)
public class KeycloakRealmImportController implements Reconciler<KeycloakRealmImport>, EventSourceInitializer<KeycloakRealmImport>, ErrorStatusHandler<KeycloakRealmImport> {

    @Inject
    KubernetesClient client;

    @Inject
    ObjectMapper jsonMapper;

    @Override
    public List<EventSource> prepareEventSources(EventSourceContext<KeycloakRealmImport> context) {
        SharedIndexInformer<Job> jobsInformer =
                client
                    .batch()
                    .v1()
                    .jobs()
                    .inAnyNamespace()
                    .withLabels(DEFAULT_LABELS)
                    .runnableInformer(0);

        return List.of(new InformerEventSource<>(
                jobsInformer, job -> {
            var ownerReferences = job.getMetadata().getOwnerReferences();
            if (!ownerReferences.isEmpty()) {
                return Set.of(new ResourceID(ownerReferences.get(0).getName(),
                        job.getMetadata().getNamespace()));
            } else {
                return Set.of();
            }
        }));
    }

    @Override
    public UpdateControl<KeycloakRealmImport> reconcile(KeycloakRealmImport realm, Context context) {
        String realmName = realm.getMetadata().getName();
        String realmNamespace = realm.getMetadata().getNamespace();

        Log.infof("--- Reconciling Keycloak Realm: %s in namespace: %s", realmName, realmNamespace);

        var statusBuilder = new KeycloakRealmImportStatusBuilder();

        var realmImportSecret = new KeycloakRealmImportSecret(client, realm, jsonMapper);
        realmImportSecret.createOrUpdateReconciled();

        var realmImportJob = new KeycloakRealmImportJob(client, realm, realmImportSecret.getSecretName());
        realmImportJob.createOrUpdateReconciled();
        realmImportJob.updateStatus(statusBuilder);

        var status = statusBuilder.build();

        Log.info("--- Realm reconciliation finished successfully");

        if (status.equals(realm.getStatus())) {
            return UpdateControl.noUpdate();
        } else {
            realm.setStatus(status);
            return UpdateControl.updateStatus(realm);
        }
    }

    @Override
    public Optional<KeycloakRealmImport> updateErrorStatus(KeycloakRealmImport realm, RetryInfo retryInfo, RuntimeException e) {
        Log.error("--- Error reconciling", e);
        KeycloakRealmImportStatus status = new KeycloakRealmImportStatusBuilder()
                .addErrorMessage("Error performing operations:\n" + e.getMessage())
                .build();

        realm.setStatus(status);
        return Optional.of(realm);
    }
}
