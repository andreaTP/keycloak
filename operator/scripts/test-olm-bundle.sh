#! /bin/bash
set -x

DOCKER_REGISTRY="docker.io/andreatp"
# DOCKER_REGISTRY="quay.io/keycloak"

# Create OLM bundle
# TODO 999-SNAPSHOT doesn't work with OLM bundles
# VERSION=$(cd .. && mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
VERSION="1.2.3"
PREV_VERSION="1.2.2"

OPERATOR_DOCKER_IMAGE="docker.io/andreatp/keycloak-operator"
# OPERATOR_DOCKER_IMAGE="quay.io/keycloak/keycloak-operator"

./scripts/create-olm-bundle.sh $VERSION $PREV_VERSION $OPERATOR_DOCKER_IMAGE

(cd olm/$VERSION && \
  docker build -t $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION -f bundle.Dockerfile . && \
  docker push $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION)

# Verify the bundle
opm alpha bundle validate --tag $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION --image-builder docker

# Create the test-catalog
./scripts/create-olm-test-catalog.sh $VERSION $DOCKER_REGISTRY/keycloak-operator-bundle

(cd olm/catalog && \
  docker build -f test-catalog.Dockerfile -t $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION . && \
  docker push $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION)

# Create testing resources

rm -rf olm/testing-resources
mkdir -p olm/testing-resources

(
  cd olm/testing-resources

  cat << EOF >> catalog.yaml
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: test-catalog
  namespace: default
spec:
  sourceType: grpc
  image: $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION
  displayName: Keycloak Test Catalog
  publisher: Me
  updateStrategy:
    registryPoll:
      interval: 10m
EOF

  cat << EOF >> operatorgroup.yaml
kind: OperatorGroup
apiVersion: operators.coreos.com/v1
metadata:
  name: og-single
  namespace: default
spec:
  targetNamespaces:
  - default
EOF

  cat << EOF >> subscription.yaml
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: keycloak-operator
  namespace: default
spec:
  installPlanApproval: Automatic
  name: keycloak-operator
  source: test-catalog
  sourceNamespace: default
  startingCSV: keycloak-operator.v$VERSION
EOF

)

./scripts/install-keycloak-operator.sh
