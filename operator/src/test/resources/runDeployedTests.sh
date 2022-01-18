cd ../../..
eval $(minikube docker-env)
kubectl create namespace keycloak-test

mvn clean package -Doperator \
    -Dquarkus.container-image.build=true \
    -Dquarkus.kubernetes.deploy=true \
    -Dquarkus.kubernetes.namespace=keycloak-test \
    -Dquarkus.kubernetes-client.namespace=keycloak-test \
    -Dquarkus.kubernetes.deployment-target=kubernetes,openshift,minikube \
    -DskipTests
mvn test -Dtest=OperatorE2ERemoteIT

kubectl delete namespace keycloak-test

