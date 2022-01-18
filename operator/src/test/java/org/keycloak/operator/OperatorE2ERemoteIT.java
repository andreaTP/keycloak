package org.keycloak.operator;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

public class OperatorE2ERemoteIT {
    private static final Logger logger = Logger.getLogger(OperatorE2ERemoteIT.class);

    @Test
    public void podDeployedTest() {
        logger.debug("Pod Deployed Test");

        new OperatorBatteryTests().execute(new DefaultKubernetesClient(), "keycloak-test");
    }
}
