#! /bin/bash
set -x

# Delete the default catalog
kubectl delete catalogsources operatorhubio-catalog -n olm | true

kubectl apply -f olm/testing-resources
