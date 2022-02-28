package org.keycloak.operator;

import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.utils.CRAssert;
import org.keycloak.operator.v2alpha1.KeycloakService;
import org.keycloak.operator.v2alpha1.crds.Keycloak;
import org.keycloak.operator.utils.K8sUtils;
import org.keycloak.operator.v2alpha1.crds.KeycloakRealmImport;
import org.keycloak.operator.v2alpha1.crds.KeycloakRealmImportStatusCondition;
import org.keycloak.operator.v2alpha1.crds.KeycloakStatusCondition;

import java.time.Duration;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;


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
                .untilAsserted(() -> CRAssert.assertKeycloakStatusCondition(crSelector.get(), KeycloakStatusCondition.READY, false));

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
                .untilAsserted(() -> CRAssert.assertKeycloakStatusCondition(crSelector.get(), KeycloakStatusCondition.READY, true));

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

    // local debug commands:
    //    export TOKEN=$(curl --data "grant_type=password&client_id=token-test-client&username=test&password=test" http://localhost:8080/realms/token-test/protocol/openid-connect/token | jq -r '.access_token')
    //
    //    curl http://localhost:8080/realms/token-test/protocol/openid-connect/userinfo -H "Authorization: bearer $TOKEN"
    //
    //    example good answer:
    //    {"sub":"b660eec6-a93b-46fd-abb2-e9fbdff67a63","email_verified":false,"preferred_username":"test"}
    //    example error answer:
    //    {"error":"invalid_request","error_description":"Token not provided"}
    @Test
    public void testKeycloakCacheIsConnected() {
        // given
        Log.info("Setup");
        var kc = K8sUtils.getDefaultKeycloakDeployment();
        var crSelector = k8sclient
                .resources(Keycloak.class)
                .inNamespace(kc.getMetadata().getNamespace())
                .withName(kc.getMetadata().getName());
        var targetInstances = 3;
        kc.getSpec().setInstances(targetInstances);
        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(kc);
        var realm = k8sclient.resources(KeycloakRealmImport.class).inNamespace(namespace).load(getClass().getResourceAsStream("/token-test-realm.yaml"));
        var realmImportSelector = k8sclient.resources(KeycloakRealmImport.class).inNamespace(namespace).withName("example-token-test-kc");
        realm.createOrReplace();

        Log.info("Waiting for a stable Keycloak Cluster");
        Awaitility.await()
                .atMost(10, MINUTES)
                .pollDelay(5, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    Log.info("Checking realm import has finished.");
                    CRAssert.assertKeycloakRealmImportStatusCondition(realmImportSelector.get(), KeycloakRealmImportStatusCondition.DONE, true);
                    Log.info("Checking Keycloak is stable.");
                    CRAssert.assertKeycloakStatusCondition(crSelector.get(), KeycloakStatusCondition.READY, true);
                });

        Log.info("Testing the Keycloak Cluster");
        Awaitility.await().atMost(5, MINUTES).ignoreExceptions().untilAsserted(() -> {
            // Get the list of Keycloak pods
            var pods = k8sclient
                    .pods()
                    .inNamespace(namespace)
                    .withLabels(Constants.DEFAULT_LABELS)
                    .list()
                    .getItems();

            String token = null;
            // Obtaining the token from the first pod
            // Connecting using port-forward and a fixed port to respect the instance issuer used hostname
            for (var pod: pods) {
                Log.info("Testing Pod: " + pod.getMetadata().getName());
                try (var portForward = k8sclient
                        .pods()
                        .inNamespace(namespace)
                        .withName(pod.getMetadata().getName())
                        .portForward(8080, 8080)) {

                    token = (token != null) ? token : RestAssured.given()
                            .param("grant_type" , "password")
                            .param("client_id", "token-test-client")
                            .param("username", "test")
                            .param("password", "test")
                            .post("http://localhost:" + portForward.getLocalPort() + "/realms/token-test/protocol/openid-connect/token")
                            .body()
                            .jsonPath()
                            .getString("access_token");

                    Log.info("Using token:" + token);

                    var username = RestAssured.given()
                            .header("Authorization",  "Bearer " + token)
                            .get("http://localhost:" + portForward.getLocalPort() + "/realms/token-test/protocol/openid-connect/userinfo")
                            .body()
                            .jsonPath()
                            .getString("preferred_username");

                    Log.info("Username found: " + username);

                    assertThat(username).isEqualTo("test");
                }
            }
        });

        // This is to test passing through the "Service", not deterministic, but a smoke test that things are working as expected
        // Performed here to avoid paying the setup time again
        var service = new KeycloakService(k8sclient, kc);
        Awaitility.await().atMost(5, MINUTES).ignoreExceptions().untilAsserted(() -> {
            String token = null;
            // Obtaining the token from the first pod
            // Connecting using port-forward and a fixed port to respect the instance issuer used hostname
            for (int i = 0; i < (targetInstances * 2); i++) {

                if (token == null) {
                    var tokenUrl = "http://" + service.getName() + "." + namespace + ":" + Constants.KEYCLOAK_SERVICE_PORT + "/realms/token-test/protocol/openid-connect/token";
                    Log.info("Checking url: " + tokenUrl);

                    var tokenOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "--data", "grant_type=password&client_id=token-test-client&username=test&password=test", tokenUrl);
                    Log.info("Curl Output with token: " + tokenOutput);
                    JsonNode tokenAnswer = Serialization.jsonMapper().readTree(tokenOutput);
                    assertThat(tokenAnswer.hasNonNull("access_token")).isTrue();
                    token = tokenAnswer.get("access_token").asText();
                }

                String url = "http://" + service.getName() + "." + namespace + ":" + Constants.KEYCLOAK_SERVICE_PORT + "/realms/token-test/protocol/openid-connect/userinfo";
                Log.info("Checking url: " + url);

                var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "-H", "Authorization: Bearer " + token, url);
                Log.info("Curl Output on access attempt: " + curlOutput);


                JsonNode answer = Serialization.jsonMapper().readTree(curlOutput);
                assertThat(answer.hasNonNull("preferred_username")).isTrue();
                assertThat(answer.get("preferred_username").asText()).isEqualTo("test");
            }
        });

        // Run a "sample get" passing through the service now

        // get the service
//        var service = new KeycloakService(k8sclient, kc);
//
//        var endpoint = k8sclient
//                .endpoints()
//                .inNamespace(namespace)
//                .withName(service.getName())
//                .get();
//
//        var ips = endpoint
//                .getSubsets()
//                .stream()
//                .flatMap(e -> e.getAddresses().stream())
//                .map(a -> a.getIp())
//                .collect(Collectors.toList());
//
//        Awaitility.await().atMost(5, MINUTES).ignoreExceptions().untilAsserted(() -> {
//            // Get the token from one instance:
//            var tokenUrl = "http://" + service.getName() + "." + namespace + ":" + Constants.KEYCLOAK_SERVICE_PORT + "/realms/token-test/protocol/openid-connect/token";
//            // for (var ip: ips) {
//            // var tokenUrl = "http://" + ip + ":" + Constants.KEYCLOAK_SERVICE_PORT + "/realms/token-test/protocol/openid-connect/token";
//            Log.info("Checking url: " + tokenUrl);
//
//            var tokenOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "--data", "grant_type=password&client_id=token-test-client&username=test&password=test", tokenUrl);
//            Log.info("Curl Output with token: " + tokenOutput);
//            JsonNode tokenAnswer = Serialization.jsonMapper().readTree(tokenOutput);
//            assertThat(tokenAnswer.hasNonNull("access_token")).isTrue();
//
//            var token = tokenAnswer.get("access_token").asText();
//
//            // Port forward to each pod
//             for (var ip: ips) {
//                String url = "http://" + ip + ":" + Constants.KEYCLOAK_SERVICE_PORT + "/realms/token-test/protocol/openid-connect/userinfo";
//                Log.info("Checking url: " + url);
//
//                var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "-H", "Authorization: Bearer " + token, url);
//                Log.info("Curl Output on access attempt: " + curlOutput);
//
//
//                JsonNode answer = Serialization.jsonMapper().readTree(curlOutput);
//                assertThat(answer.hasNonNull("preferred_username")).isTrue();
//                assertThat(answer.get("preferred_username").asText()).isEqualTo("test");
//            }
//        });
    }
}
