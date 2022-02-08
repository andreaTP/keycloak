package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.extended.run.RunConfigBuilder;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.v2alpha1.KeycloakService;
import org.keycloak.operator.v2alpha1.crds.Keycloak;
import org.keycloak.operator.v2alpha1.crds.KeycloakRealmImport;
import org.keycloak.operator.v2alpha1.crds.KeycloakRealmImportStatusCondition;

import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.keycloak.operator.Constants.KEYCLOAK_SERVICE_PORT;
import static org.keycloak.operator.utils.K8sUtils.getDefaultKeycloakDeployment;
import static org.keycloak.operator.utils.K8sUtils.inClusterCurl;
import static org.keycloak.operator.v2alpha1.crds.KeycloakRealmImportStatusCondition.DONE;
import static org.keycloak.operator.v2alpha1.crds.KeycloakRealmImportStatusCondition.STARTED;
import static org.keycloak.operator.v2alpha1.crds.KeycloakRealmImportStatusCondition.HAS_ERRORS;

@QuarkusTest
public class RealmImportE2EIT extends ClusterOperatorTest {

    private KeycloakRealmImportStatusCondition getCondition(List<KeycloakRealmImportStatusCondition> conditions, String type) {
        return conditions
                .stream()
                .filter(c -> c.getType().equals(type))
                .findFirst()
                .get();
    }

    @Test
    public void testWorkingRealmImport() {
        // Arrange
        k8sclient.load(getClass().getResourceAsStream("/example-keycloak.yml")).inNamespace(namespace).createOrReplace();

        // Act
        k8sclient.load(getClass().getResourceAsStream("/example-realm.yaml")).inNamespace(namespace).createOrReplace();

        // Assert
        var crSelector = k8sclient
                .resources(KeycloakRealmImport.class)
                .inNamespace(namespace)
                .withName("example-count0-kc");
        Awaitility.await()
                .atMost(3, MINUTES)
                .pollDelay(5, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var conditions = crSelector
                            .get()
                            .getStatus()
                            .getConditions();

                    assertThat(getCondition(conditions, DONE).getStatus()).isFalse();
                    assertThat(getCondition(conditions, STARTED).getStatus()).isTrue();
                    assertThat(getCondition(conditions, HAS_ERRORS).getStatus()).isFalse();
                });

        Awaitility.await()
                .atMost(3, MINUTES)
                .pollDelay(5, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var conditions = crSelector
                            .get()
                            .getStatus()
                            .getConditions();

                    assertThat(getCondition(conditions, DONE).getStatus()).isTrue();
                    assertThat(getCondition(conditions, STARTED).getStatus()).isFalse();
                    assertThat(getCondition(conditions, HAS_ERRORS).getStatus()).isFalse();
                });
        var service = new KeycloakService(k8sclient, getDefaultKeycloakDeployment());
        String url =
                "http://" + service.getName() + "." + namespace + ":" + KEYCLOAK_SERVICE_PORT + "/realms/count0";

        Awaitility.await().atMost(5, MINUTES).untilAsserted(() -> {
            Log.info("Starting curl Pod to test if the realm is available");
            Log.info("Url: '" + url + "'");
            String curlOutput = inClusterCurl(k8sclient, namespace, url);
            Log.info("Output from curl: '" + curlOutput + "'");
            assertThat(curlOutput).isEqualTo("200");
        });
    }

    @Test
    public void testNotWorkingRealmImport() {
        // Arrange
        k8sclient.load(getClass().getResourceAsStream("/example-keycloak.yml")).inNamespace(namespace).createOrReplace();

        // Act
        k8sclient.load(getClass().getResourceAsStream("/incorrect-realm.yaml")).inNamespace(namespace).createOrReplace();

        // Assert
        Awaitility.await()
                .atMost(3, MINUTES)
                .pollDelay(5, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var conditions = k8sclient
                            .resources(KeycloakRealmImport.class)
                            .inNamespace(namespace)
                            .withName("example-count0-kc")
                            .get()
                            .getStatus()
                            .getConditions();

                    assertThat(getCondition(conditions, HAS_ERRORS).getStatus()).isTrue();
                    assertThat(getCondition(conditions, DONE).getStatus()).isFalse();
                    assertThat(getCondition(conditions, STARTED).getStatus()).isFalse();
                });
    }

}
