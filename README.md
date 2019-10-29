# SpringBoot & ConfigMaps Demos

Demos of different Spring Boot integrations with OpenShift ConfigMaps. The main goal is to demonstrate the different ways that Spring Boot can load a properties file from Kubernetes ConfigMaps.

## Table of Contents

  * [Pre-Requisites](#pre-requisites)
    + [Tools](#tools)
    + [a. Create Projects](#a-create-projects)
  * [Demo 1: Deploy Basic ConfigMap](#demo-1-deploy-basic-configmap)
    + [a. Demo 1 Project Structure](#a-demo-1-project-structure)
      - [i. Spring Boot Application Architecture](#i-spring-boot-application-architecture)
      - [ii. Application Containerization](#ii-application-containerization)
      - [iii. Kubernetes ConfigMap and Deployment Configuration](#iii-kubernetes-configmap-and-deployment-configuration)
      - [iv. Zero-Downtime Deployment Configuration](#iv-zero-downtime-deployment-configuration)
    + [b. Deploy Demo 1 ConfigMap and Application](#b-deploy-demo-1-configmap-and-application)
    + [c. Demo 1 Cleanup](#c-demo-1-cleanup)
  * [Demo 2: Automatically Redeploy Spring Boot App Using ConfigMap Reloader](#demo-2-automatically-redeploy-spring-boot-app-using-configmap-reloader)
    + [b. Deploy Reloader](#b-deploy-reloader)
    + [b. Deploy Demo 2 ConfigMap and Application](#b-deploy-demo-2-configmap-and-application)
    + [c. Demo 2 Cleanup](#c-demo-2-cleanup)
  * [Demo 3: Use Spring Cloud Kubernetes to Refresh Config Beans without Restarting the App](#demo-3-use-spring-cloud-kubernetes-to-refresh-config-beans-without-restarting-the-app)
    + [a. Demo 3 Project Structure](#a-demo-3-project-structure)
      - [i. Spring Cloud Kubernetes Application Project Architecture](#i-spring-cloud-kubernetes-application-project-architecture)
      - [ii. Spring Cloud Kubernetes Configuration](#ii-spring-cloud-kubernetes-configuration)
      - [iii. Spring Cloud Kubernetes Service Account](#iii-spring-cloud-kubernetes-service-account)
    + [b. Deploy Demo 3 ConfigMap and Application](#b-deploy-demo-3-configmap-and-application)
    + [c. Demo 3 Cleanup](#c-demo-3-cleanup)
  * [Cluster Cleanup](#cluster-cleanup)

## Pre-Requisites

### Tools

You will need the following tools to get the demos up and running:

* OpenShift cluster:
  * You can use [minishift](https://docs.okd.io/latest/minishift/getting-started/index.html) to run a 1-node local OpenShift 3.11 cluster.
* `oc`, the OpenShift Command Line tool, which should come with `OpenShift` installation.
* [kubectl v1.15 or above](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
  * The first demo requires us to run the `kubectl rollout restart` command, which is only available in version 1.15 of the `kubectl` client.
  * If you are using minishift, you will get version 1.11 of the k8s client, so we need to download `kubectl` version 1.15 or above to get some of the demo features working.
* [helm](https://v3.helm.sh/docs/intro/install/)
  * We need `helm v3` to install the ConfigMap reloader.
  * Note: In a future iteration, I will try to remove the `helm` requirement.

### a. Create Projects

Each demo will take place in its own project. We will also need to create a separate `reloader` project for the ConfigMap reloader in demo 2. Here is a list of all the projects you will create:

* Demo 1: `config1`
* Demo 2:`config2` and `reloader`
* Demo 3: `config3`

To create the projects, run the following commands:

```bash
# Create the projects
oc new-project reloader;
oc new-project config1;
oc new-project config2;
oc new-project config3;
```

## Demo 1: Deploy Basic ConfigMap

In this demo, we are going to deploy a basic Spring Boot project into OpenShift that reads its configuration from an ConfigMap. Once it's started, the application periodically prints a message that can be overridden by providing a path to a new properties file upon starting the application.

This approach works wells for projects where the ConfigMap is static and doesn't change very often, if at all. If the ConfigMap changes, the way to have the Application Deployment pick up the new changes is by manually restarting the pod by either deleting the running pod or by triggering a rolling upgrade.

Before running the demo, let's go over the basic project structure and explain its Kubernetes configuration.

### a. Demo 1 Project Structure

#### i. Spring Boot Application Architecture

The way this project is implemented is as follows:

* Basic Spring Boot as shown in its [build.gradle](1-basic-configmap/build.gradle) file.
* The application has a [MyConfig.java](1-basic-configmap/src/main/java/com/accenture/basicconfigmap/MyConfig.java) config class that loads the [application.properties](1-basic-configmap/src/main/resources/application.properties) into a POJO object.
* Then there is a [MyBean.java](1-basic-configmap/src/main/java/com/accenture/basicconfigmap/MyConfig.java) bean that loads the above class and prints the value of its `message` property every 5 seconds. The scheduling is done via the `@EnableScheduling` annotation in the [BasicConfigMapApplication.java](1-basic-configmap/src/main/java/com/accenture/basicconfigmap/BasicConfigMapApplication.java#8) main class.
* For more details on the project structure, checkout the [1-basic-configmap](1-basic-configmap) folder.

#### ii. Application Containerization

The project is packaged using this [Dockefile](1-basic-configmap/Dockerfile), which has been built and pushed to [fabiogomezdiaz/basic-configmap:latest](https://cloud.docker.com/repository/docker/fabiogomezdiaz/basic-configmap)

#### iii. Kubernetes ConfigMap and Deployment Configuration

The project is deployed into OpenShift using the [1-basic-configmap/k8s/deployment.yaml](1-basic-configmap/k8s/deployment.yaml) file, which contains the following Kubernetes resources:

* `ConfigMap`, which holds the contents of the [application.properties](1-basic-configmap/k8s/deployment.yaml#9) file that we are going to load into Spring Boot application.
* `Deployment`, which contains the following information related to the container:
  * In [spec.template.spec.containers[0].name](1-basic-configmap/k8s/deployment.yaml#32) and [spec.template.spec.containers[0].image](1-basic-configmap/k8s/deployment.yaml#32) we define the container name and its container image.
  * In [spec.template.spec.volumes](1-basic-configmap/k8s/deployment.yaml#32) we are defining the volumes that are available to be mounted in the container, in our case being the `application.properties` field from the ConfigMap.
  * In [spec.template.spec.containers[0].volumeMounts](1-basic-configmap/k8s/deployment.yaml#46) we specify the path in the application container where the ConfigMap's `application.properties` file will be mounted (`/etc/config` in this case)
* In [spec.template.spec.containers[0].env](1-basic-configmap/k8s/deployment.yaml#44) we set the `SPRING_CONFIG_LOCATION` environment variable to tell Spring Boot the location of the above `application.properties` file at runtime.

The above configuration demonstrates how to mount a ConfigMap into a Spring Boot container. The following section will go over the Deployment settings that are required to trigger a Zero-Downtime rolling upgrade upon deploying ConfigMap updates.

#### iv. Zero-Downtime Deployment Configuration

As mentioned above earlier, when updating the ConfigMaps, we have to manually trigger a rolling upgrade for the container to pick up the ConfigMap changes. At the same, we will like to trigger the upgrade with Zero-Downtime.

By default, when triggering a rolling upgrade, Kubernetes kills the old pod and starts a new pod at the same time. The problem with this is that, while the old pod gets killed immediately, there is no guarantee that the new pod will start and immediately be able to start receiving traffic, which will cause downtime. To prevent that, Kubernetes must know the following information:

* Whether the container is alive.
* Whether the container is ready to receive traffic.
* How many containers are allowed to be unavailable during a rolling upgrade (spoiler: 0!).
* How many new pods to start simultaneously during a rolling upgrade.
  * This setting prevents too many pods from starting and consuming too many resources, which could also kill healthy running containers.

With the above criteria on mind, the following explains how the above was actually implemented in the application's `Deployment`.

* [spec.template.spec.containers[0].readinessProbe](1-basic-configmap/k8s/deployment.yaml#52) and [spec.template.spec.containers[0].livenessProbe](1-basic-configmap/k8s/deployment.yaml#59) tell Kubernetes to test application health using the Spring Boot's `actuator` health endpoints. This will be useful to achieve Zero-Downtime when triggering rolling updates after ConfigMap changes.
* On addition to the probes above, to achieve Zero-Downtime we need to tell Kubernetes to allow no pods to be unavailable while rolling out a new update, which is done in the [spec.strategy.rollingUpdate.maxUnavailable](1-basic-configmap/k8s/deployment.yaml#25) field.
* Lastly, in order to prevent Kubernetes from spinning up too many new pods at once and consume too many resources (which can actually kill healthy and critical pods), we will tell Kubernetes to start a single new pod, wait for it to be up and running (via liveness and readiness probes) and only then it can kill the old pod. This setting can be specified configured at [spec.strategy.rollingUpdate.maxSurge](1-basic-configmap/k8s/deployment.yaml#25).

Now that we have understand the basics, let's go ahead and deploy the first demo application!

### b. Deploy Demo 1 ConfigMap and Application

We will be deploying both the `ConfigMap` and the `Deployment` in the `config1` project. To do so, run the following command:

```bash
# Switch to config1 project
oc project config1

# Create ConfigMap and Deployment
oc apply -f 1-basic-configmap/k8s/deployment.yaml
```

Now, let's open a web browser tab to see the application running in Minishift dashboard. But first, we need to get the Minishift IP Address with the following command:

```bash
minishift ip
```

Now copy the IP Address output from above, go to your browser and enter the URL in the following format, which will take you to the `Pods` page in the `config1` project:

* <https://MINISHIFT_IP:8443/console/project/config1/browse/pods>

You should see the pod running and a `Containers Ready` status of `1/1` as shown below:

![Pods](static/demo-1-1-pods.png?raw=true)

Now click on the pod name (shown as `basic-configmap-xxxxxxxxxx-xxxxx` in blue), then click on the `Logs` tab and you should be able to see the `Hello from OpenShift!` message as shown below:

![Pods](static/demo-1-2-logs.png?raw=true)

The above message came from the ConfigMap's [application.properties](1-basic-configmap/k8s/deployment.yaml#10) file, which overrides the default message shown in the [MyConfig.java](1-basic-configmap/src/main/java/com/accenture/basicconfigmap/MyConfig.java#10) class.

Now that we can confirm that the application is reading the message in the ConfigMap's `application.properties` file, let's go ahead and change the message in the ConfigMap and see if the application can read and print the new message without requiring building and deploying a new Container Image.

To do so, first let's go back to the Pods list page in the following URL:

* <https://MINISHIFT_IP:8443/console/project/config1/browse/pods>

Now let's open a new tab bar and go to the `ConfigMaps` list page using the following URL:

* <https://MINISHIFT_IP:8443/console/project/config1/browse/config-maps>

You should see the `basic-configmap` listed as shown here:

![ConfigMap](static/demo-1-3-configmap.png?raw=true)

Click on the `basic-configmap`, then edit the ConfigMap by first clicking on `Actions->Edit`, which should take you to the edit page. Now change the value of `bean.message` to something such as "New Value", then click on `Save` button.

Now go back to the Pods list tab in the other browser tab.

Remember, in order for the pods to show the new message, we have to manually trigger a rolling upgrade, which will bring up a new pod that will read the new ConfigMap changes and kill the old pod once the new pod is up and running.

In order to see how the above takes place, I recommend you have the Pods page in your browser open next to your terminal window as the upgrade can happen very fast.

To trigger the rolling upgrade, run the following command on your terminal window:

```bash
# After making a change to the ConfigMap, run the following command to do a perform upgrade
kubectl -n config1 rollout restart deploy/basic-configmap
```

If everything went correctly, you should see in the OpenShift console how a new pod gets created while keeping the old one alive until the new ones is ready, as shown below:

![Upgrade](static/demo-1-4-upgrade.png?raw=true)

Now click on the new pod name, then go to the `Logs` tab and you should be able to see the new message printed out, showing that the it successfully read the new configuration at run time without requiring to build a new Container Image!

### c. Demo 1 Cleanup

Before moving to the next demo, let's delete both the ConfigMap and Deployment from Demo 1 with the following command:

```bash
# Delete ConfigMap and Deployment
oc delete -f 1-basic-configmap/k8s/deployment.yaml
```

## Demo 2: Automatically Redeploy Spring Boot App Using ConfigMap Reloader

In the previous demo, we learned how a Spring Boot application can read a properties file from a ConfigMap at run time without having to build and deploy a new Container Image, which can save you lots of time and avoid having multiple images for, say, multiple environments.

However, for an application that has configuration that's constantly changing, having to manually trigger a rolling upgrade to detect the new changes can become impractical and time consuming. The new changes could also easily be missed unless an automated process is in place.

In this demo, you will learn how to setup a system that automatically triggers rolling upgrades on a deployment when its corresponding ConfigMap is updated. Then you will test the automatic rolling upgrade by making a ConfigMap change and seeing a new pod get created automatically.

### b. Deploy Reloader

In order for Demo 2 to work, you will need to deploy the [`reloader`](https://github.com/stakater/Reloader) Kubernetes Controller. This component will watch changes in ConfigMap and Secrets and do rolling upgrades on Pods with their associated Deployment, StatefulSet, DaemonSet and DeploymentConfig.

To deploy it, we are going to use the publicly available helm chart. To do so, run the following commands as admin.

```bash
# Make sure you are logged in as an admin
# If using minishift, run this command to become admin
oc login -u system:admin

# Deploy reloader
oc project reloader;
helm repo add stakater https://stakater.github.io/stakater-charts;
helm repo update;
helm upgrade --install reloader stakater/reloader --namespace reloader;
```

Now you can log back in as a regular user with the following command:

```bash
oc login -u developer -p developer
```

### b. Deploy Demo 2 ConfigMap and Application

From this point on, the process is very similar to Demo 1. In fact, we will be deploying the same application and the same ConfigMap and Deployment.

The only difference is that the Deployment will have a new annotation field in [metadata.annotations](2-configmap-reloader/k8s/deployment.yaml#20), which looks similar to the following:

```yaml
  annotations:
    reloader.stakater.com/auto: "true"
```

The above annotation is how `reloader` knows that a given Deployment will require a rolling upgrade when its corresponding ConfigMap is updated.

NOTE: For more details on how `reloader` works, feel free to checkout the project page at [https://github.com/stakater/Reloader](https://github.com/stakater/Reloader).

To test the automatic rolling upgrade, I recommend you open 2 separate browser tabs with the following links and put them side to side so you can see the updates as it happens.

* `https://MINISHIFT_IP:8443/console/project/config2/browse/pods`
  * This is the `Pods` page in the `config2` project.
* `https://MINISHIFT_IP:8443/console/project/config2/browse/config-maps`
  * This is the `ConfigMaps` page in the `config2` project.

Now, on your terminal window

```bash
# Switch to config2 project
oc project config2

# Create ConfigMap and Deployment
oc apply -f 2-configmap-reloader/k8s/deployment.yaml
```

Then make a change on the ConfigMap page and watch how `reloader` detects the ConfigMap change and automatically rolls out a new pod in the Pods page.

### c. Demo 2 Cleanup

Before moving to the next demo, let's delete both the ConfigMap and Deployment from Demo 1 with the following command:

```bash
# Delete ConfigMap and Deployment
oc delete -f 2-configmap-reloader/k8s/deployment.yaml
```

## Demo 3: Use Spring Cloud Kubernetes to Refresh Config Beans without Restarting the App

Demo 1 and 2 showed us how we can read the contents of an `application.properties` file from a ConfigMap at run time and automatically rollout an upgrade with Zero-Downtime when the Configmap changes.

However, certain applications might not have the luxury to wait for the rolling upgrade to complete as this process can be slow. Instead, a near real-time approach might be necessary.

This problem can be solved using [Spring Cloud Kubernetes](https://spring.io/projects/spring-cloud-kubernetes), which (through [Service Accounts](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)) allows your application to listen for  ConfigMap changes (via Kubernetes API events) and automatically reload the new configuration into your application without triggering a rollout update or simply restarting the application.

On Demo 3, you will deploying a very similar application to that of Demo 1, which gets its `application.properties` from a ConfigMap. The only difference being that it includes the 

### a. Demo 3 Project Structure

#### i. Spring Cloud Kubernetes Application Project Architecture

The way this project is implemented is very similar as Demo 1 with the following differences:

* The Spring Cloud Kubernetes dependency is defined in its [3-spring-cloud-k8s-refresh/build.gradle](3-spring-cloud-k8s-refresh/build.gradle#21) file.
* The Spring Cloud Kubernetes settings, explained in the next section, are defined in [3-spring-cloud-k8s-refresh/src/main/resources/bootstrap.yaml](3-spring-cloud-k8s-refresh/src/main/resources/bootstrap.yaml)
  * The `bootstrap.yaml` is a properties file that gets loaded before the `application.properties` or `application.yaml` file.
  * Since the Spring Cloud Kubernetes settings won't change and we are overriding the `application.properties` file, it makes sense to leave those properties in `bootstrap.yaml` as overriding those settings at run time can cause unexpected results.
* For more details on the project structure, checkout the [3-spring-cloud-k8s-refresh](3-spring-cloud-k8s-refresh) folder.

#### ii. Spring Cloud Kubernetes Configuration

For the application to automatically detect ConfigMap changes and reload them in real-time without restarting the application, you need configure the Spring Cloud Kubernetes (SCK) settings in `bootstrap.yaml`, as shown below:

```yaml
spring:
  application:
    name: spring-cloud-k8s-refresh
  cloud:
    kubernetes:
      config:
        enabled: true
        enableApi: true
        sources:
          - name: ${spring.application.name}
      reload:
        enabled: true
        mode: event
        strategy: refresh
        monitoring-config-maps: true
        monitoring-secrets: false
```

Where:

* `spring.cloud.kubernetes.config.enabled` is the setting that tells SCK to watch for ConfigMap changes.
* `spring.cloud.kubernetes.config.enableApi` is where you tell SCK to use the Kubernetes API to watch for ConfigMap changes.
* `spring.cloud.kubernetes.config.sources` is a list of sources (ConfigMaps in our case) to watch for changes.
  * In this case we are watching a ConfigMap with the same name as the application
* `spring.cloud.kubernetes.reload.enabled` is where you enable the reload feature.
* `spring.cloud.kubernetes.reload.mode` is the mode in which SCK will listen for changes (Kubernetes API changes in our case).
* `spring.cloud.kubernetes.reload.strategy` is the configuration reload strategy to use when a new ConfigMap event happens.
  * In our case we use the `refresh` strategy, which will reload the new configuration without restarting the application.
* `spring.cloud.kubernetes.reload.monitoring-config-maps` is how you tell SCK whether to watch for ConfigMap changes.

For more Spring Cloud Kubernetes configuration details, checkout the following links on Spring Cloud Kubernetes's GitHub page:

* <https://github.com/spring-cloud/spring-cloud-kubernetes#configmap-propertysource>
* <https://github.com/spring-cloud/spring-cloud-kubernetes#53-propertysource-reload>

#### iii. Spring Cloud Kubernetes Service Account

In order for Spring Cloud Kubernetes to listen to Kubernetes API events on ConfigMap updates, the application pod needs its Service Account to have the required permissions on the API server. To do so you must declare a [Role](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#role-and-clusterrole) with the required permissions (mainly read access to ConfigMaps) and bind it to the Service Account using a [RoleBinding](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#rolebinding-and-clusterrolebinding).

In our case, we are going to bind the role to the `default` ServiceAccount that comes on each namespace, instead of creating a new ServiceAccount.

Checkout the Role and RoleBinding we created for the demo in [3-spring-cloud-k8s-refresh/k8s/sa.yaml](3-spring-cloud-k8s-refresh/k8s/sa.yaml).

### b. Deploy Demo 3 ConfigMap and Application

Now that we understand how Spring Cloud Kubernetes' reload feature works, let's go ahead and deploy it.

```bash
# Switch to config3 project
oc project config3

# Make sure you are logged in as an admin
# If using minishift, run this command to become admin
oc login -u system:admin

# Create Role and bind it to default ServiceAccount
oc apply -f 3-spring-cloud-k8s-refresh/k8s/sa.yaml

# Then you can switch back to a regular user:
oc login -u developer -p developer

# Create ConfigMap and Deployment
oc apply -f 3-spring-cloud-k8s-refresh/k8s/deployment.yaml
```

Now that everything is deploy, let's open 2 browser tabs side by side. On the first one you will open the `Logs` tab of the pod in the `config3` project. On the second tab you will open the corresponding ConfigMap.

Now go ahead and make a change on the ConfigMap and watch how `Spring Cloud Kubernetes` detects the ConfigMap change, refreshes the Config Bean, and starts printing the new message without restarting the application.

### c. Demo 3 Cleanup

```bash
# Delete ConfigMap and Deployment
oc delete -f 3-spring-cloud-k8s-refresh/k8s/deployment.yaml
```

## Cluster Cleanup

```bash
# Make sure you are logged in as an admin
# Delete reloader
oc login -u system:admin
oc project reloader;
helm delete reloader;

# Delete projects
oc delete project reloader;
oc delete project config1;
oc delete project config2;
oc delete project config3;
```
