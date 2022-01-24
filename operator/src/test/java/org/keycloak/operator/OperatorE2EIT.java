package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.v2alpha1.crds.Keycloak;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class OperatorE2EIT extends ClusterOperatorTest {

    @Test
    public void given_ClusterAndOperatorRunning_when_KeycloakCRCreated_Then_KeycloakStructureIsDeployedAndStatusIsOK() {
        logger.info(((testremote) ? "Remote " : "Local ") + "Run Test :" + operator + " -- " + namespace);

        // Node
        List<Node> nodes = k8sclient.nodes().list().getItems();
        assertThat(nodes).hasSize(1);

        // NS created by the extension [ probably this doesnt make sense as if not passes, then extension would fail]
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(k8sclient.namespaces().withName(namespace).get()).isNotNull());
        assertThat(k8sclient.namespaces().withName(namespace + "XX").get()).isNull();

        // CR
        Resource<Keycloak> keycloakResource = k8sclient.resources(Keycloak.class).load("kubernetes/example-keycloak.yml");
        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(keycloakResource.get());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(k8sclient.resources(Keycloak.class).inNamespace(namespace).withName("example-kc").get()).isNotNull());

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
                            logger.info("Exception getting the log :" + e.getStackTrace());
                        }
                        assertThat(k8sclient.apps().deployments().inNamespace(namespace).withName("keycloak").get().getStatus().getReadyReplicas()).isEqualTo(1);
                    });
        } catch (ConditionTimeoutException e) {
            logger.info("On error POD LOG " + podlog);
            throw e;
        }


    }

}
