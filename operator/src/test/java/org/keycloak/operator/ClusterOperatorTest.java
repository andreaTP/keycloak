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

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ClusterOperatorTest {
  public static final String TARGET_KUBERNETES_MINIKUBE_YML = "target/kubernetes/minikube.yml";
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

    if (testremote) {
      createRBACresourcesAndOperatorDeployment();
    } else {
      createOperator();
      registerReconcilers();
      operator.start();
    }

  }

  private void createK8sClient() {
    k8sclient = new DefaultKubernetesClient(new ConfigBuilder(Config.autoConfigure(null)).withNamespace(namespace).build());
  }

  private void createRBACresourcesAndOperatorDeployment() throws FileNotFoundException {
    logger.info("Creating RBAC into Namespace " + namespace);
    k8sclient.load(new FileInputStream(TARGET_KUBERNETES_MINIKUBE_YML)).createOrReplace();
  }

  private void cleanRBACresourcesAndOperatorDeployment() throws FileNotFoundException {
    logger.info("Deleting RBAC from Namespace " + namespace);
    k8sclient.load(new FileInputStream(TARGET_KUBERNETES_MINIKUBE_YML)).delete();
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
  public void after() throws FileNotFoundException {
    logger.info("Cleaning up namespace : " + namespace);

    cleanRBACresourcesAndOperatorDeployment();
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
