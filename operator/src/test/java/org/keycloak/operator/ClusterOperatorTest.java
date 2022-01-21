package org.keycloak.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkiverse.operatorsdk.runtime.OperatorProducer;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.*;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ClusterOperatorTest {
  @Inject
  Logger logger;

  @ConfigProperty(name = "testremote", defaultValue = "false")
  Boolean testremote;

  @Inject
  Instance<Reconciler<? extends HasMetadata>> reconcilers;

  @Inject
  QuarkusConfigurationService configuration;

  protected Operator operator;
  protected KubernetesClient k8sclient;
  protected String namespace;

  @BeforeEach
  public void before() throws FileNotFoundException {
    calculateNamespace();
    createK8sClient();
    createNamespace();

    if (!testremote) {
      createOperator();
      registerReconcilers();
      operator.start();
      createRBACresources();
    }

  }

  private void createK8sClient() {
    k8sclient = new DefaultKubernetesClient(new ConfigBuilder(Config.autoConfigure(null)).withNamespace(namespace).build());
  }

  private void createRBACresources() throws FileNotFoundException {
    // ROLE, BINDING, SERVICEACCOUNT
    logger.info("Creating RBAC into Namespace " + namespace);
    InputStream resourceAsStreamRBAC = new FileInputStream("target/kubernetes/minikube.yml");

    k8sclient.load(resourceAsStreamRBAC).get().stream()
            .filter(a -> !a.getKind().equalsIgnoreCase("Deployment") || testremote)
            .forEach(res -> {
              logger.info("...........Deploying : " + res.getKind() + " | " + res.getMetadata().getName());
              k8sclient.resource(res).inNamespace(namespace).createOrReplace();
            });
  }

  private void registerReconcilers() {
    logger.info("Registering reconcilers for operator : " + operator + " [" + testremote + "]");

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
    namespace = "keycloak-test-" + UUID.randomUUID();
  }

  @AfterEach
  public void after() {
    logger.info("Cleaning up namespace : " + namespace);

    assertThat(k8sclient.namespaces().withName(namespace).delete()).isTrue();
    Awaitility
            .await()
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> assertThat(k8sclient.namespaces().withName(namespace).get()).isNull());
    if (!testremote) {
      operator.stop();
    }
  }
}
