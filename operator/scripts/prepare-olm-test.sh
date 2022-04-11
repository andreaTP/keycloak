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

(cd $SCRIPT_DIR/../olm/$VERSION && \
  docker build -t $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION -f bundle.Dockerfile . && \
  docker push $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION)

# Verify the bundle
opm alpha bundle validate --tag $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION --image-builder docker

# Create the test-catalog
# Using `sudo` to make the command working on Mac:
sudo opm index add --bundles $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION \
  --tag $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION -c docker
docker push $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION

# Create testing resources
# TODO clean this up
# $SCRIPT_DIR/create-olm-test-resources.sh $VERSION $DOCKER_REGISTRY
