package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.utils.K8sUtils;
import org.keycloak.operator.v2alpha1.KeycloakService;
import org.keycloak.operator.v2alpha1.crds.Keycloak;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.keycloak.operator.Constants.DEFAULT_LABELS;
import static org.keycloak.operator.utils.K8sUtils.*;

@QuarkusTest
public class KeycloakIngressE2EIT extends ClusterOperatorTest {

    @Test
    public void testIngressOnHTTP() {
        var kc = getDefaultKeycloakDeployment();
        kc.getSpec().setHostname(Constants.INSECURE_DISABLE);
        kc.getSpec().setTlsSecret(Constants.INSECURE_DISABLE);
        deployKeycloak(k8sclient, kc, true);

        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var output = RestAssured.given()
                            .get("http://" + kubernetesIp + ":80/realms/master")
                            .body()
                            .jsonPath()
                            .getString("realm");

                    assertEquals("master", output);
                });
    }

    @Test
    public void testIngressOnHTTPS() {
        var kc = getDefaultKeycloakDeployment();
        kc.getSpec().setHostname(Constants.INSECURE_DISABLE);
        deployKeycloak(k8sclient, kc, true);

        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var output = RestAssured.given()
                            .relaxedHTTPSValidation()
                            .get("https://" + kubernetesIp + ":443/realms/master")
                            .body()
                            .jsonPath()
                            .getString("realm");

                    assertEquals("master", output);
                });
    }

}
