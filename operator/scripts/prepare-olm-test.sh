#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

DOCKER_REGISTRY="$1"

VERSION="$2"

OPERATOR_IMAGE_NAME="keycloak-operator"
OPERATOR_DOCKER_IMAGE="$DOCKER_REGISTRY/$OPERATOR_IMAGE_NAME"

# Create OLM bundle
# TODO: clean this up
# $SCRIPT_DIR/create-olm-bundle.sh $VERSION $PREV_VERSION $OPERATOR_DOCKER_IMAGE

# (cd $SCRIPT_DIR/../olm/$VERSION && \
#   docker build -t $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION -f bundle.Dockerfile . && \
#   docker push $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION)

# Verify the bundle
# ttl.sh
# opm alpha bundle validate --tag $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION --image-builder docker

# Create the test-catalog
# Using `sudo` to make the command working on Mac:
sudo opm index add --bundles $DOCKER_REGISTRY/keycloak-operator-bundle:$VERSION \
  --tag $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION -c docker --permissive

# sudo opm index add --bundles 10.96.98.60/keycloak-operator-bundle:0.0.1-fc642f5   --tag=10.96.98.60/keycloak-test-catalog:0.0.1-fc642f5 -c docker -p docker

docker push $DOCKER_REGISTRY/keycloak-test-catalog:$VERSION

# Create testing resources
# TODO clean this up
# $SCRIPT_DIR/create-olm-test-resources.sh $VERSION $DOCKER_REGISTRY
