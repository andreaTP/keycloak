package org.keycloak.operator;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.utils.CRAssert;
import org.keycloak.operator.v2alpha1.KeycloakService;
import org.keycloak.operator.v2alpha1.crds.Keycloak;
import org.keycloak.operator.v2alpha1.crds.KeycloakStatusCondition;
import org.keycloak.operator.utils.K8sUtils;

import java.time.Duration;
import java.util.List;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.keycloak.operator.Constants.KEYCLOAK_DISCOVERY_SERVICE_PORT;
import static org.keycloak.operator.v2alpha1.crds.KeycloakStatusCondition.READY;

@QuarkusTest
public class ClusteringE2EIT extends ClusterOperatorTest {

    @Test
    public void testKeycloakScaleAsExpected() {
        // given
        var kc = K8sUtils.getDefaultKeycloakDeployment();
        var crSelector = k8sclient
                .resources(Keycloak.class)
                .inNamespace(kc.getMetadata().getNamespace())
                .withName(kc.getMetadata().getName());
        K8sUtils.deployKeycloak(k8sclient, kc, true);

        var kcPodsSelector = k8sclient.pods().inNamespace(namespace).withLabel("app", "keycloak");

        Keycloak keycloak = k8sclient.resources(Keycloak.class)
                .inNamespace(namespace)
                .list().getItems().get(0);

        // when scale it to 10
        keycloak.getSpec().setInstances(10);
        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(keycloak);

        Awaitility.await()
                .atMost(1, MINUTES)
                .pollDelay(1, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> CRAssert.assertKeycloakStatusCondition(crSelector.get(), READY, false));

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(kcPodsSelector.list().getItems().size()).isEqualTo(10));

        // when scale it down to 2
        keycloak.getSpec().setInstances(2);
        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(keycloak);
        Awaitility.await()
                .atMost(Duration.ofSeconds(180))
                .untilAsserted(() -> assertThat(kcPodsSelector.list().getItems().size()).isEqualTo(2));

        Awaitility.await()
                .atMost(2, MINUTES)
                .pollDelay(5, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> CRAssert.assertKeycloakStatusCondition(crSelector.get(), READY, true));

        // get the service
        var service = new KeycloakService(k8sclient, kc);
        String url = "http://" + service.getName() + "." + namespace + ":" + Constants.KEYCLOAK_SERVICE_PORT;

        Awaitility.await().atMost(5, MINUTES).untilAsserted(() -> {
            Log.info("Starting curl Pod to test if the realm is available");
            Log.info("Url: '" + url + "'");
            String curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, url);
            Log.info("Output from curl: '" + curlOutput + "'");
            assertThat(curlOutput).isEqualTo("200");
        });
    }

}
