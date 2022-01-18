package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.jboss.logging.Logger;
import org.keycloak.operator.v2alpha1.crds.Keycloak;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OperatorBatteryTests {
    private static final Logger logger = Logger.getLogger(OperatorBatteryTests.class);

    public void execute(KubernetesClient k8sclient, String namespace) {
        // Node
        List<Node> nodes = k8sclient.nodes().list().getItems();
        assertThat(nodes).hasSize(1);

        // NS created by the extension [ probably this doesnt make sense as if not passes, then extension would fail]
        assertThat(k8sclient.namespaces().withName(namespace).get()).isNotNull();
        assertThat(k8sclient.namespaces().withName(namespace + "XX").get()).isNull();

        assertThat(k8sclient.rbac().clusterRoles().withName("keycloakcontroller-cluster-role").get()).isNotNull();
        assertThat(k8sclient.serviceAccounts().inNamespace(namespace).withName("keycloak-operator").get()).isNotNull();

        // CR
        Resource<Keycloak> keycloakResource = k8sclient.resources(Keycloak.class).load("src/main/resources/example-keycloak.yml");
        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(keycloakResource.get());

        assertThat(k8sclient.resources(Keycloak.class).inNamespace(namespace).withName("example-kc").get()).isNotNull();

        // Check Operator has deployed Keycloak
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollDelay(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(k8sclient.apps().deployments().inNamespace(namespace).withName("keycloak").get()).isNotNull());

        // Check Keycloak has status ready
        StringBuffer podlog = new StringBuffer();
        try {
            Awaitility.await()
                    .atMost(Duration.ofSeconds(30L))
                    .pollDelay(Duration.ofSeconds(5))
                    .untilAsserted(() -> {
                        k8sclient.pods().inNamespace(namespace).list().getItems().stream()
                                .filter(a -> a.getMetadata().getName().startsWith("keycloak"))
                                .forEach(a -> podlog.append(a.getMetadata().getName()).append(" : ").append(k8sclient.pods().inNamespace(namespace).withName(a.getMetadata().getName()).getLog(true)));
                        assertThat(k8sclient.apps().deployments().inNamespace(namespace).withName("keycloak").get().getStatus().getReadyReplicas()).isEqualTo(1);
                    });
        } catch (ConditionTimeoutException e) {
            logger.debug("On error POD LOG " + podlog);
            throw e;
        }
    }

}
