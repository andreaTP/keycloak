#! /bin/bash
set -euxo pipefail

VERSION=$1
REPLACES_VERSION=$2
OPERATOR_DOCKER_IMAGE=$3

CREATED_AT=$(date "+%D %T")

echo "Creating OLM bundle for version $VERSION replacing version $REPLACES_VERSION"

rm -rf olm/$VERSION
mkdir -p olm/$VERSION

cp -r olm-base/* olm/$VERSION

# Inject RBAC rules
yq ea '.rules as $item ireduce ({}; .rules += $item)' target/kubernetes/kubernetes.yml | \
  yq ea -i 'select(fileIndex==0).spec.install.spec.permissions[0] = select(fileIndex==1) | select(fileIndex==0)' olm/$VERSION/manifests/clusterserviceversion.yaml - && \
yq ea -i '.spec.install.spec.permissions[0].serviceAccountName = "keycloak-operator"' olm/$VERSION/manifests/clusterserviceversion.yaml && \
yq ea -i ".metadata.annotations.containerImage = \"$OPERATOR_DOCKER_IMAGE:$VERSION\"" olm/$VERSION/manifests/clusterserviceversion.yaml && \
yq ea -i ".metadata.annotations.createdAt = \"$CREATED_AT\"" olm/$VERSION/manifests/clusterserviceversion.yaml && \
yq ea -i ".metadata.name = \"keycloak-operator.v$VERSION\"" olm/$VERSION/manifests/clusterserviceversion.yaml && \
yq ea -i ".spec.install.spec.deployments[0].spec.template.spec.containers[0].image = \"$OPERATOR_DOCKER_IMAGE:$VERSION\"" olm/$VERSION/manifests/clusterserviceversion.yaml && \
yq ea -i ".spec.replaces = \"keycloak-operator.v$REPLACES_VERSION\"" olm/$VERSION/manifests/clusterserviceversion.yaml && \
yq ea -i ".spec.version = \"$VERSION\"" olm/$VERSION/manifests/clusterserviceversion.yaml

mv olm/$VERSION/manifests/clusterserviceversion.yaml "olm/$VERSION/manifests/keycloak-operator.v$VERSION.clusterserviceversion.yaml"

cp target/kubernetes/*.keycloak.org-v1.yml olm/$VERSION/manifests
