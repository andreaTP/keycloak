package org.keycloak.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.operator.v2alpha1.KeycloakController;
import org.keycloak.operator.v2alpha1.crds.Keycloak;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class OperatorE2ELocalRunTest {
    private static final Logger logger = Logger.getLogger(OperatorE2ELocalRunTest.class);

    @RegisterExtension
    OperatorExtension operator =
            OperatorExtension.builder()
                    .withConfigurationService(DefaultConfigurationService.instance())
                    .waitForNamespaceDeletion(false)
                    .withReconciler(KeycloakController.class)
                    .build();

    private void setup(KubernetesClient k8sclient, String namespace) throws FileNotFoundException {
        // CRD
        File fileCRDS = new File("target/kubernetes/keycloaks.keycloak.org-v1.yml");
        k8sclient.apiextensions().v1().customResourceDefinitions().createOrReplace(k8sclient.apiextensions().v1().customResourceDefinitions().load(fileCRDS).get());

        // ROLE, BINDING, SERVICEACCOUNT
        InputStream resourceAsStreamRBAC = new FileInputStream("target/kubernetes/minikube.yml");
        k8sclient.load(resourceAsStreamRBAC).get().stream().filter(a -> !a.getKind().equalsIgnoreCase("Deployment")).forEach(res -> k8sclient.resource(res).inNamespace(namespace).createOrReplace());
    }

    @Test
    public void localRunTest() throws FileNotFoundException {
        logger.debug("Local Run Test");
        KubernetesClient k8sclient = operator.getKubernetesClient();
        String namespace = operator.getNamespace();

        setup(k8sclient, namespace);

        new OperatorBatteryTests().execute(k8sclient, namespace);
    }

}
