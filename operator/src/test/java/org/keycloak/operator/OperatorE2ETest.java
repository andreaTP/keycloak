package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkiverse.operatorsdk.runtime.OperatorProducer;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.operator.v2alpha1.crds.Keycloak;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class OperatorE2ETest {
    @Inject
    Logger logger;


    @ConfigProperty(name = "testremote")
    Optional<Boolean> testremote;

    @Inject
    Instance<Reconciler<? extends HasMetadata>> reconcilers;

    @Inject
    QuarkusConfigurationService configuration;

    @Inject
    KubernetesClient k8sclient;
    Operator operator;
    String namespace;

    @BeforeEach
    public void setup() throws FileNotFoundException {
        calculateNamespace();
        createNamespace();

        if (testremote.isEmpty()) {
            createOperator();
            registerReconcilers();
            operator.start();
        }

        createRBACresources();
    }

    private void createRBACresources() throws FileNotFoundException {
        // ROLE, BINDING, SERVICEACCOUNT
        logger.info("Creating RBAC into Namespace " + namespace);
        InputStream resourceAsStreamRBAC = new FileInputStream("target/kubernetes/minikube.yml");

        k8sclient.load(resourceAsStreamRBAC).get().stream()
              .filter(a -> !a.getKind().equalsIgnoreCase("Deployment") || testremote.isPresent())
              .forEach(res -> {
                  logger.info("...........Deploying : " + res.getKind() + " | " + res.getMetadata().getName());
                  k8sclient.resource(res).inNamespace(namespace).createOrReplace();
              });
    }

    private void registerReconcilers() {
        logger.info("Registering reconcilers for operator : " + operator + " [" + testremote.isPresent() + "]");

        for (Reconciler<? extends HasMetadata> reconciler : reconcilers) {
            final var config = configuration.getConfigurationFor(reconciler);
            if (!config.isRegistrationDelayed()) {
                logger.info("Register and apply : " + reconciler.getClass().getName());
                OperatorProducer.applyCRDIfNeededAndRegister(operator, reconciler, configuration);
            }
        }
    }

    private void createOperator() {
        operator = new Operator(k8sclient, configuration);
        operator.getConfigurationService().getClientConfiguration().setNamespace(namespace);
    }

    private void createNamespace() {
        k8sclient.namespaces().createOrReplace(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());
    }

    private void calculateNamespace() {
        namespace = "keycloak-test-" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    @AfterEach
    public void cleanup() {
        logger.info("Cleaning up namespace : " + namespace);

        assertThat(k8sclient.namespaces().withName(namespace).delete()).isTrue();
        Awaitility
                .await()
                .atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(k8sclient.namespaces().withName(namespace).get()).isNull());
    }

    @Test
    public void localRunTest() {
        logger.info("++++++++++++++++++++++++++++++ Local Run Test :" + operator + " -- " + namespace);
        testOperator();
        logger.info("************* FINISHED TEST 1 !!!!!!!! ");

    }
    @Test
    public void localRunTest2() {
        logger.info("++++++++++++++++++++++++++++++ Local Run Test 2 :" + operator + " -- " + namespace);
        testOperator();
        logger.info("************* FINISHED TEST 2 !!!!!!!! ");
    }

    @Test
    public void localRunTest3() {
        logger.info("++++++++++++++++++++++++++++++ Local Run Test 3 :" + operator + " -- " + namespace);
        testOperator();
        logger.info("************* FINISHED TEST 3 !!!!!!!! ");
    }

    private void testOperator() {
        // Node
        List<Node> nodes = k8sclient.nodes().list().getItems();
        assertThat(nodes).hasSize(1);

        // NS created by the extension [ probably this doesnt make sense as if not passes, then extension would fail]
        assertThat(k8sclient.namespaces().withName(namespace).get()).isNotNull();
        assertThat(k8sclient.namespaces().withName(namespace + "XX").get()).isNull();

        assertThat(k8sclient.rbac().clusterRoles().withName("keycloakcontroller-cluster-role").get()).isNotNull();
        assertThat(k8sclient.serviceAccounts().inNamespace(namespace).withName("keycloak-operator").get()).isNotNull();

        // CR
        Resource<Keycloak> keycloakResource = k8sclient.resources(Keycloak.class).load("kubernetes/example-keycloak.yml");
        k8sclient.resources(Keycloak.class).inNamespace(namespace).createOrReplace(keycloakResource.get());

        assertThat(k8sclient.resources(Keycloak.class).inNamespace(namespace).withName("example-kc").get()).isNotNull();

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
