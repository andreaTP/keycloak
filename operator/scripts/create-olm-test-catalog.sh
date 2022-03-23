#! /bin/bash
set -x

VERSION=$1
BUNDLE_IMAGE=$2

rm -rf olm/catalog
mkdir -p olm/catalog/test-catalog

(
  cd olm/catalog

  opm generate dockerfile test-catalog

  opm init keycloak-operator \
    --default-channel=alpha \
    --output yaml > test-catalog/operator.yaml

  opm render $BUNDLE_IMAGE:$VERSION \
    --output=yaml >> test-catalog/operator.yaml

  cat << EOF >> test-catalog/operator.yaml
---
schema: olm.channel
package: keycloak-operator
name: alpha
entries:
  - name: keycloak-operator.v$VERSION
EOF

  opm validate test-catalog
)
