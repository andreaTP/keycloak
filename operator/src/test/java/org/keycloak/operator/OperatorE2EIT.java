package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.v2alpha1.crds.Keycloak;
import org.keycloak.operator.v2alpha1.crds.KeycloakSpec;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class OperatorE2EIT extends ClusterOperatorTest {

    @Test
    public void given_ClusterAndOperatorRunning_when_KeycloakCRCreated_Then_KeycloakStructureIsDeployedAndStatusIsOK() throws Exception {
        Log.info(((deployment == OperatorDeployment.remote) ? "Remote " : "Local ") + "Run Test :" + namespace);
        var keycloakName = "example-kc";

        // CR
        // TODO: sprinkle the CR with lombok and sundrio
        var keycloak = new Keycloak();
        keycloak.setMetadata(new ObjectMetaBuilder()
                .withName(keycloakName)
                .withNamespace(namespace)
                .build());
        var spec = new KeycloakSpec();
        spec.setInstances(1);
        spec.setServerConfiguration(Map.of());
        keycloak.setSpec(spec);

        k8sclient
                .resources(Keycloak.class)
                .create(keycloak);

        // Check Operator has deployed Keycloak
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollDelay(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(k8sclient.apps().deployments().inNamespace(namespace).withName(keycloakName).get()).isNotNull());

        // Check Keycloak has status ready
        StringBuffer podlog = new StringBuffer();
        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(180))
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
                        assertThat(k8sclient.apps().deployments().inNamespace(namespace).withName(keycloakName).get().getStatus().getReadyReplicas()).isEqualTo(1);
                    });
        } catch (ConditionTimeoutException e) {
            Log.info("On error POD LOG " + podlog);
            throw e;
        }


    }

}
