package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.ObjectAssert;
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
    public void localRunTest() throws InterruptedException {
        logger.info("++++++++++++++++++++++++++++++ Local Run Test :" + operator + " -- " + namespace);
        testOperator();
        logger.info("************* FINISHED TEST 1 !!!!!!!! ");

    }
    @Test
    public void localRunTest2() throws InterruptedException {
        logger.info("++++++++++++++++++++++++++++++ Local Run Test 2 :" + operator + " -- " + namespace);
        testOperator();
        logger.info("************* FINISHED TEST 2 !!!!!!!! ");
    }

    @Test
    public void localRunTest3() throws InterruptedException {
        logger.info("++++++++++++++++++++++++++++++ Local Run Test 3 :" + operator + " -- " + namespace);
        testOperator();
        logger.info("************* FINISHED TEST 3 !!!!!!!! ");
    }

    private void testOperator() throws InterruptedException {
        // Node
        List<Node> nodes = k8sclient.nodes().list().getItems();
        assertThat(nodes).hasSize(1);

        // NS created by the extension [ probably this doesnt make sense as if not passes, then extension would fail]
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(k8sclient.namespaces().withName(namespace).get()).isNotNull());
        assertThat(k8sclient.namespaces().withName(namespace + "XX").get()).isNull();

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(k8sclient.rbac().clusterRoles().withName("keycloakcontroller-cluster-role").get()).isNotNull());
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(k8sclient.serviceAccounts().inNamespace(namespace).withName("keycloak-operator").get()).isNotNull());

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
