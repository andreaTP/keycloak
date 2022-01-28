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
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ClusterOperatorTest {

  public static final String KEYCLOAK_OPERATOR_DEPLOYMENT = "keycloak.operator.deployment";
  public static final String QUARKUS_KUBERNETES_DEPLOYMENT_TARGET = "quarkus.kubernetes.deployment-target";

  public static final String TARGET_KUBERNETES_MINIKUBE_YML = "target/kubernetes/";

  enum OperatorDeployment {local,remote}

  protected static Instance<Reconciler<? extends HasMetadata>> reconcilers;
  protected static QuarkusConfigurationService configuration;
  protected static KubernetesClient k8sclient;
  protected static String namespace;
  protected static String deploymentTarget;
  protected static OperatorDeployment deployment;
  private static Operator operator;

  @BeforeAll
  public static void before() throws Exception {
    configuration = CDI.current().select(QuarkusConfigurationService.class).get();
    reconcilers = CDI.current().select(new TypeLiteral<>() {});
    var config = ConfigProvider.getConfig();
    deployment = config.getValue(KEYCLOAK_OPERATOR_DEPLOYMENT, OperatorDeployment.class);
    deploymentTarget = config.getOptionalValue(QUARKUS_KUBERNETES_DEPLOYMENT_TARGET, String.class).orElse("kubernetes");

    calculateNamespace();
    createK8sClient();
    createNamespace();

    if (deployment == OperatorDeployment.remote) {
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

  private static void createRBACresourcesAndOperatorDeployment() throws Exception {
    Log.info("Creating RBAC into Namespace " + namespace);
    // fix subject[0].namespace for ClusterRoleBinding
    // TODO: this can be more elegant or, better, done with Kustomize
    var resources = Files.readAllLines(Path.of(TARGET_KUBERNETES_MINIKUBE_YML, deploymentTarget+".yml"));
    var sb = new StringBuilder();
    for (var res: resources) {
      sb.append(res.replaceAll("NAMESPACE-PLACEHOLDER", namespace) + "\n");
    }

    k8sclient
            .load(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)))
            .inNamespace(namespace)
            .createOrReplace();
  }

  private static void cleanRBACresourcesAndOperatorDeployment() throws FileNotFoundException {
    Log.info("Deleting RBAC from Namespace " + namespace);
    k8sclient.load(new FileInputStream(TARGET_KUBERNETES_MINIKUBE_YML+deploymentTarget+".yml")).delete();
  }

  private static void registerReconcilers() {
    Log.info("Registering reconcilers for operator : " + operator + " [" + deployment + "]");

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
  public static void after() throws Exception {
    Log.info("Cleaning up namespace : " + namespace);

    if (deployment == OperatorDeployment.remote) {
      cleanRBACresourcesAndOperatorDeployment();
    }

    assertThat(k8sclient.namespaces().withName(namespace).delete()).isTrue();
    Awaitility
            .await()
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> assertThat(k8sclient.namespaces().withName(namespace).get()).isNull());

    if (deployment == OperatorDeployment.local) {
      operator.stop();
    }
  }
}
