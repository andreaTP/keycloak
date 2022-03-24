#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

VERSION="$1"
JAVA_OPTS="$2"

yq ea -i ".spec.install.spec.deployments[0].spec.template.spec.containers[0].env += {\"name\": \"JAVA_OPTS\", \"value\": \"$JAVA_OPTS\"}" olm/0.0.2/manifests/keycloak-operator.v0.0.2.clusterserviceversion.yaml
