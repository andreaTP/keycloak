#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

VERSION="$1"
VALUE="$2"

yq ea -i ".spec.install.spec.deployments[0].spec.template.spec.containers[0].env += {\"name\": \"OPERATOR_KEYCLOAK_IMAGE\", \"value\": \"$VALUE\"}" $SCRIPT_DIR/../olm/$VERSION/manifests/keycloak-operator.v$VERSION.clusterserviceversion.yaml
