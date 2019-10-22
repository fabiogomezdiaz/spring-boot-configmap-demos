# SpringBoot & ConfigMaps Demos

## Pre-Requisites

### a. Create Namespaces

```bash
# Make sure you are logged in as an admin
oc create ns reloader;
oc create ns config1;
oc create ns config2;
oc create ns config3;
```

### b. Deploy Reloader

This is the ConfigMap reloader.

```bash
# Make sure you are logged in as an admin
oc project reloader;
helm repo add stakater https://stakater.github.io/stakater-charts;
helm repo update;
helm upgrade --install reloader stakater/reloader --namespace reloader;
```

Now you can log back in as a regular user

## 1. Deploy Basic ConfigMap

```bash
oc apply -f 1-basic-configmap/k8s/deployment.yaml
```

```bash
kubectl -n config1 rollout restart deploy/basic-configmap
```

```bash
oc delete -f 1-basic-configmap/k8s/deployment.yaml
```

## 2. Automatically Redeploy Spring Boot App Using ConfigMap Reloader

```bash
oc apply -f 2-configmap-reloader/k8s/deployment.yaml
```

```bash
oc delete -f 2-configmap-reloader/k8s/deployment.yaml
```

## 3. Use Spring Cloud Kubernetes to Refresh Config Beans without Restarting the App

```bash
oc apply -f 3-spring-cloud-k8s-refresh/k8s/sa.yaml
oc apply -f 3-spring-cloud-k8s-refresh/k8s/deployment.yaml
```

```bash
oc delete -f 3-spring-cloud-k8s-refresh/k8s/deployment.yaml
oc delete -f 3-spring-cloud-k8s-refresh/k8s/sa.yaml
```

## Cleanup

```bash
# Make sure you are logged in as an admin
# Delete reloader
oc project reloader;
helm delete reloader;

# Delete namespaces
oc delete ns reloader;
oc delete ns config1;
oc delete ns config2;
oc delete ns config3;
```
