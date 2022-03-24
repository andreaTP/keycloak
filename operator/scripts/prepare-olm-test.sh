#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

DOCKER_REGISTRY="$1"

VERSION="$2"
PREV_VERSION="$3"

OPERATOR_IMAGE_NAME="keycloak-operator"
OPERATOR_DOCKER_IMAGE="$DOCKER_REGISTRY/$OPERATOR_IMAGE_NAME"

# Create OLM bundle
$SCRIPT_DIR/create-olm-bundle.sh $VERSION $PREV_VERSION $OPERATOR_DOCKER_IMAGE

# WARNING: This should be done ONLY for tests
$SCRIPT_DIR/inject-custom-image.sh $VERSION "-Doperator.keycloak.image=$DOCKER_REGISTRY/keycloak:$VERSION"

(cd $SCRIPT_DIR/../olm/$VERSION && \
  docker build -t $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION -f bundle.Dockerfile . && \
  docker push $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION)

# Verify the bundle
opm alpha bundle validate --tag $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION --image-builder docker

# Create the test-catalog
$SCRIPT_DIR/create-olm-test-catalog.sh $VERSION $DOCKER_REGISTRY/keycloak-operator-bundle

(cd $SCRIPT_DIR/../olm/catalog && \
  docker build -f test-catalog.Dockerfile -t $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION . && \
  docker push $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION)

# Create testing resources
$SCRIPT_DIR/create-olm-test-resources.sh $VERSION $DOCKER_REGISTRY
