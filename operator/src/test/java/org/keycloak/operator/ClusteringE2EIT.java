package org.keycloak.operator;

import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.extended.run.RunConfigBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.utils.CRAssert;
import org.keycloak.operator.v2alpha1.KeycloakService;
import org.keycloak.operator.v2alpha1.crds.Keycloak;
import org.keycloak.operator.utils.K8sUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.keycloak.operator.v2alpha1.crds.KeycloakStatusCondition.READY;

@QuarkusTest
public class ClusteringE2EIT extends ClusterOperatorTest {

//    @Test
//    public void testKeycloakScaleAsExpected() {
//        // given
//        var kc = K8sUtils.getDefaultKeycloakDeployment();
//        var crSelector = k8sclient
//                .resources(Keycloak.class)
//                .inNamespace(kc.getMetadata().getNamespace())
//                .withName(kc.getMetadata().getName());
//        K8sUtils.deployKeycloak(k8sclient, kc, true);
//
//        var kcPodsSelector = k8sclient.pods().inNamespace(namespace).withLabel("app", "keycloak");
//
//        Keycloak keycloak = k8sclient.resources(Keycloak.class)
//                .inNamespace(namespace)
//                .list().getItems().get(0);
//
//        // when scale it to 10
//        keycloak.getSpec().setInstances(10);
//        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(keycloak);
//
//        Awaitility.await()
//                .atMost(1, MINUTES)
//                .pollDelay(1, SECONDS)
//                .ignoreExceptions()
//                .untilAsserted(() -> CRAssert.assertKeycloakStatusCondition(crSelector.get(), READY, false));
//
//        Awaitility.await()
//                .atMost(Duration.ofSeconds(5))
//                .untilAsserted(() -> assertThat(kcPodsSelector.list().getItems().size()).isEqualTo(10));
//
//        // when scale it down to 2
//        keycloak.getSpec().setInstances(2);
//        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(keycloak);
//        Awaitility.await()
//                .atMost(Duration.ofSeconds(180))
//                .untilAsserted(() -> assertThat(kcPodsSelector.list().getItems().size()).isEqualTo(2));
//
//        Awaitility.await()
//                .atMost(2, MINUTES)
//                .pollDelay(5, SECONDS)
//                .ignoreExceptions()
//                .untilAsserted(() -> CRAssert.assertKeycloakStatusCondition(crSelector.get(), READY, true));
//
//        // get the service
//        var service = new KeycloakService(k8sclient, kc);
//        String url = "http://" + service.getName() + "." + namespace + ":" + Constants.KEYCLOAK_SERVICE_PORT;
//
//        Awaitility.await().atMost(5, MINUTES).untilAsserted(() -> {
//            Log.info("Starting curl Pod to test if the realm is available");
//            Log.info("Url: '" + url + "'");
//            String curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, url);
//            Log.info("Output from curl: '" + curlOutput + "'");
//            assertThat(curlOutput).isEqualTo("200");
//        });
//    }

// notes:
//    export TOKEN=$(curl --data "grant_type=password&client_id=token-test-client&username=test&password=test" http://localhost:8080/realms/token-test/protocol/openid-connect/token | jq -r '.access_token')
//
//    curl http://localhost:8080/realms/token-test/protocol/openid-connect/userinfo -H "Authorization: bearer $TOKEN"
//
//    example good answer:
//    {"sub":"b660eec6-a93b-46fd-abb2-e9fbdff67a63","email_verified":false,"preferred_username":"test"}%
//    example error answer:
//    {"error":"invalid_request","error_description":"Token not provided"}%
//
//    in the cluster (e.g. ssh into the postgres pod `apt update && apt install curl`):
//    curl --data "grant_type=password&client_id=token-test-client&username=test&password=test" http://example-kc-service:8080/realms/token-test/protocol/openid-connect/token
//    curl http://example-kc-service:8080/realms/token-test/protocol/openid-connect/userinfo -H "Authorization: bearer $TOKEN"

    @Test
    public void testKeycloakCacheIsConnected() {
        // given
        var kc = K8sUtils.getDefaultKeycloakDeployment();
        var crSelector = k8sclient
                .resources(Keycloak.class)
                .inNamespace(kc.getMetadata().getNamespace())
                .withName(kc.getMetadata().getName());
        kc.getSpec().setInstances(3);
        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(kc);
        k8sclient.load(getClass().getResourceAsStream("/token-test-realm.yaml")).inNamespace(namespace).createOrReplace();

        Awaitility.await()
                .atMost(2, MINUTES)
                .pollDelay(5, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> CRAssert.assertKeycloakStatusCondition(crSelector.get(), READY, true));

        // get the service
        var service = new KeycloakService(k8sclient, kc);

        var endpoint = k8sclient
                .endpoints()
                .inNamespace(namespace)
                .withName(service.getName())
                .get();

        var ips = endpoint
                .getSubsets()
                .stream()
                .flatMap(e -> e.getAddresses().stream())
                .map(a -> a.getIp())
                .collect(Collectors.toList());

        Awaitility.await().atMost(5, MINUTES).untilAsserted(() -> {
            // Get the token from one instance:
            var tokenUrl = "http://" + service.getName() + "." + namespace + ":" + Constants.KEYCLOAK_SERVICE_PORT + "/realms/token-test/protocol/openid-connect/token";
            Log.info("Checking url: " + tokenUrl);

            var tokenOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "--data", "grant_type=password&client_id=token-test-client&username=test&password=test", tokenUrl);
            Log.info("Curl Output with token: " + tokenOutput);
            JsonNode tokenAnswer = Serialization.jsonMapper().readTree(tokenOutput);
            assertThat(tokenAnswer.hasNonNull("access_token")).isTrue();

            var token = tokenAnswer.get("access_token").asText();

            for (var ip: ips) {
                String url = "http://" + ip + ":" + Constants.KEYCLOAK_SERVICE_PORT + "/realms/token-test/protocol/openid-connect/userinfo";
                Log.info("Checking url: " + url);

                var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "-H", "Authorization: Bearer " + token, url);
                Log.info("Curl Output on access attempt: " + curlOutput);

                JsonNode answer = Serialization.jsonMapper().readTree(curlOutput);
                assertThat(answer.hasNonNull("preferred_username")).isTrue();
                assertThat(answer.get("preferred_username")).isEqualTo("test");
            }
        });
    }
}
