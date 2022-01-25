package org.keycloak.operator;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.v2alpha1.crds.Keycloak;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class OperatorE2EIT extends ClusterOperatorTest {

    @Test
    public void given_ClusterAndOperatorRunning_when_KeycloakCRCreated_Then_KeycloakStructureIsDeployedAndStatusIsOK() {
        Log.info(((operatorDeployment == OperatorDeployment.remote) ? "Remote " : "Local ") + "Run Test :" + namespace);

        // CR
        Resource<Keycloak> keycloakResource = k8sclient.resources(Keycloak.class).load("src/main/resources/example-keycloak.yml");
        k8sclient.resources(Keycloak.class).inNamespace(namespace).create(keycloakResource.get());

        // Check Operator has deployed Keycloak
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollDelay(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(k8sclient.apps().deployments().inNamespace(namespace).withName("keycloak").get()).isNotNull());

        // Check Keycloak has status ready
        StringBuffer podlog = new StringBuffer();
        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(60))
                    .pollDelay(Duration.ofSeconds(5))
                    .untilAsserted(() -> {
                        try {
                            k8sclient.pods().inNamespace(namespace).list().getItems().stream()
                                    .filter(a -> a.getMetadata().getName().startsWith("keycloak"))
                                    .forEach(a -> podlog.append(a.getMetadata().getName()).append(" : ")
                                            .append(k8sclient.pods().inNamespace(namespace).withName(a.getMetadata().getName()).getLog(true)));
                        } catch (KubernetesClientException e) {
                            Log.info("Exception getting the log :" + e.getStackTrace());
                        }
                        assertThat(k8sclient.apps().deployments().inNamespace(namespace).withName("keycloak").get().getStatus().getReadyReplicas()).isEqualTo(1);
                    });
        } catch (ConditionTimeoutException e) {
            Log.info("On error POD LOG " + podlog);
            throw e;
        }


    }

}
