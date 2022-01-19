#!/bin/bash

cd ..
eval $(minikube docker-env)
kubectl create namespace keycloak-test

mvn -B package -Doperator \
            -Dquarkus.container-image.push=true \
            -Dquarkus.container-image.group=keycloak \
            -Dquarkus.container-image.additional-tags=latest-jar \
            -DskipTests \
            --no-transfer-progress
mvn test -Dtest=OperatorE2ERemoteIT

kubectl delete namespace keycloak-test

