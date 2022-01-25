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
import io.quarkus.logging.Log;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.TypeLiteral;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ClusterOperatorTest {

  public enum OperatorDeployment {local,remote}

  public static final String OPERATOR_DEPLOYMENT_PROP = "operator.deployment";
  public static final String TARGET_KUBERNETES_MINIKUBE_YML = "target/kubernetes/minikube.yml";

  protected static OperatorDeployment operatorDeployment;
  protected static Instance<Reconciler<? extends HasMetadata>> reconcilers;
  protected static QuarkusConfigurationService configuration;
  protected static KubernetesClient k8sclient;
  protected static String namespace;
  private static Operator operator;

  @BeforeAll
  public static void before() throws FileNotFoundException {
    configuration = CDI.current().select(QuarkusConfigurationService.class).get();
    operatorDeployment = ConfigProvider.getConfig().getOptionalValue(OPERATOR_DEPLOYMENT_PROP, OperatorDeployment.class).orElse(OperatorDeployment.local);
    reconcilers = CDI.current().select(new TypeLiteral<>() {});

    calculateNamespace();
    createK8sClient();
    createNamespace();

    if (operatorDeployment == OperatorDeployment.remote) {
      createRBACresourcesAndOperatorDeployment();
    } else {
      createOperator();
      registerReconcilers();
      operator.start();
    }

  }

  private static void createK8sClient() {
    k8sclient = new DefaultKubernetesClient(new ConfigBuilder(Config.autoConfigure(null)).withNamespace(namespace).build());
  }

  private static void createRBACresourcesAndOperatorDeployment() throws FileNotFoundException {
    Log.info("Creating RBAC into Namespace " + namespace);
    k8sclient.load(new FileInputStream(TARGET_KUBERNETES_MINIKUBE_YML)).createOrReplace();
  }

  private static void cleanRBACresourcesAndOperatorDeployment() throws FileNotFoundException {
    Log.info("Deleting RBAC from Namespace " + namespace);
    k8sclient.load(new FileInputStream(TARGET_KUBERNETES_MINIKUBE_YML)).delete();
  }

  private static void registerReconcilers() {
    Log.info("Registering reconcilers for operator : " + operator + " [" + operatorDeployment + "]");

    for (Reconciler reconciler : reconcilers) {
      final var config = configuration.getConfigurationFor(reconciler);
      if (!config.isRegistrationDelayed()) {
        Log.info("Register and apply : " + reconciler.getClass().getName());
        OperatorProducer.applyCRDIfNeededAndRegister(operator, reconciler, configuration);
      }
    }
  }

  private static void createOperator() {
    operator = new Operator(k8sclient, configuration);
    operator.getConfigurationService().getClientConfiguration().setNamespace(namespace);
  }

  private static void createNamespace() {
    k8sclient.namespaces().createOrReplace(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());
  }

  private static void calculateNamespace() {
    namespace = "keycloak-test-" + UUID.randomUUID();
  }

  @AfterAll
  public static void after() throws FileNotFoundException {
    Log.info("Cleaning up namespace : " + namespace);

    if (operatorDeployment == OperatorDeployment.remote) {
      cleanRBACresourcesAndOperatorDeployment();
    }

    assertThat(k8sclient.namespaces().withName(namespace).delete()).isTrue();
    Awaitility
            .await()
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> assertThat(k8sclient.namespaces().withName(namespace).get()).isNull());

    if (operatorDeployment == OperatorDeployment.local) {
      operator.stop();
    }
  }
}
