#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

VERSION="$1"
JAVA_OPTS="$2"

yq ea -i ".spec.install.spec.deployments[0].spec.template.spec.containers[0].env += {\"name\": \"JAVA_OPTS\", \"value\": \"$JAVA_OPTS\"}" $SCRIPT_DIR/../olm/$VERSION/manifests/keycloak-operator.v$VERSION.clusterserviceversion.yaml
