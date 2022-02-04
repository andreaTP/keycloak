package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.extended.run.RunConfigBuilder;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.v2alpha1.crds.KeycloakRealmImport;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.keycloak.operator.v2alpha1.crds.KeycloakRealmStatusCondition.*;

@QuarkusTest
public class RealmImportE2EIT extends ClusterOperatorTest {

    @Test
    public void testRealmImportHappyPath() {
        Log.info(((operatorDeployment == OperatorDeployment.remote) ? "Remote " : "Local ") + "Run Test :" + namespace);
        // Arrange
        k8sclient.load(getClass().getResourceAsStream("/example-postgres.yaml")).inNamespace(namespace).createOrReplace();
        k8sclient.load(getClass().getResourceAsStream("/example-keycloak.yml")).inNamespace(namespace).createOrReplace();

        // Act
        k8sclient.load(getClass().getResourceAsStream("/example-realm.yaml")).inNamespace(namespace).createOrReplace();

        // Assert
        Awaitility.await()
                .atMost(Duration.ofSeconds(180))
                .pollDelay(Duration.ofSeconds(5))
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var conditions = k8sclient
                            .resources(KeycloakRealmImport.class)
                            .inNamespace(namespace)
                            .withName("example-count0-kc")
                            .get()
                            .getStatus()
                            .getConditions();
                    var done = conditions
                            .stream()
                            .filter(c -> c.getType().equals(DONE))
                            .findFirst()
                            .get();
                    var started = conditions
                            .stream()
                            .filter(c -> c.getType().equals(STARTED))
                            .findFirst()
                            .get();
                    var error = conditions
                            .stream()
                            .filter(c -> c.getType().equals(HAS_ERRORS))
                            .findFirst()
                            .get();

                    assertThat(done.getStatus()).isTrue();
                    assertThat(started.getStatus()).isFalse();
                    assertThat(error.getStatus()).isFalse();
                });

        // Create a service to access Keycloak through http
        k8sclient.services().inNamespace(namespace).create(
                new ServiceBuilder()
                        .withNewMetadata()
                        .withName("example-keycloak")
                        .endMetadata()
                        .withNewSpec()
                        .withSelector(Map.of("app", "keycloak"))
                        .addNewPort()
                        .withPort(8080)
                        .endPort()
                        .endSpec()
                        .build()
        );

        String url =
                "http://example-keycloak." + namespace + ":8080/realms/count0";

        Awaitility.await().atMost(5, MINUTES).untilAsserted(() -> {
            try {
                Log.info("Starting curl Pod to test if the realm is available");

                Pod curlPod = k8sclient.run().inNamespace(namespace)
                        .withRunConfig(new RunConfigBuilder()
                                .withArgs("-s", "-o", "/dev/null", "-w", "%{http_code}", url)
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
                Log.info("Output from curl: '" + curlOutput + "'");
                assertThat(curlOutput).isEqualTo("200");
            } catch (KubernetesClientException ex) {
                throw new AssertionError(ex);
            } finally {
                Log.info("Deleting curl Pod");
                k8sclient.pods().inNamespace(namespace).withName("curl").delete();
                Awaitility.await().atMost(1, MINUTES)
                        .until(() -> k8sclient.pods().inNamespace(namespace).withName("curl")
                                .get() == null);
            }
        });
    }

}
