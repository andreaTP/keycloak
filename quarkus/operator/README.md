# Keycloak on Quarkus

The module holds the codebase to build the Keycloak Operator on top of [Quarkus](https://quarkus.io/).
Using the [Quarkus Operator SDK](https://github.com/quarkiverse/quarkus-operator-sdk).

## Activating the Module

When build from the project root directory, this module is only enabled if the installed JDK is 17 or newer. 

## Building

Ensure you have JDK 17 (or newer) installed.

Build the Docker image with:

```bash
mvn clean package -Dquarkus.container-image.build=true
```

## Contributing

### Quick start on Minikube

Enable the Minikube Docker daemon:

```bash
eval $(minikube -p minikube docker-env)
```

Compile the project and generate the Docker image with JIB:

```bash
mvn clean package -Dquarkus.container-image.build=true -Dquarkus.kubernetes.deployment-target=minikube
```

Install the CRD definition in the cluster:

```bash
kubectl apply -f target/kubernetes/keycloaks.keycloak.org-v1.yml
```

And install the operator:

```bash
rm -f kustomize/base/*.json
rm -f kustomize/base/*.yml
cp -f target/kubernetes/* kustomize/base
kubectl apply -k kustomize/base
```
