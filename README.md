# Edge Open Demo Project

## Edge Gateway (based on RHEL with Microshift preinstalled)
To use the prebuilt image, create a VM using the provided ISO as installation disk found [ftp link](https://bit.ly/3Bo6occ)  
As operating system select RHEL8.6, create a VM with the disk image as the ISO and make sure to select EUFI as boot loader, provide at least 4GB of RAM and choose automatic partioning  
Username and password of default admin user are *admin:password*  
After the installation Microshift should be running already, you can check with:
```
systemctl status microshift
```
You would need to install oc or kubectl to access the cluster:  
```
curl -O https://mirror.openshift.com/pub/openshift-v4/$(uname -m)/clients/ocp/stable/openshift-client-linux.tar.gz
sudo tar -xf openshift-client-linux.tar.gz -C /usr/local/bin oc kubectl
```
and copy the kubeconfig file:  
```
mkdir ~/.kube
sudo cat /var/lib/microshift/resources/kubeadmin/kubeconfig > ~/.kube/config
```
Now you should be able to access the microshift cluster

## MQTT Broker
The broker is based on Apache Artemis MQ [project](https://activemq.apache.org/components/artemis/)  
We will be using this qoricode/activemq-artemis image to create a [statefulset](ss-artemis.yaml) and [service](svc-artemis.yaml)  
By default persistence is enabled and all protocol ports are exposed (port 8161 dedicated to web console and port 1883 dedicated to mqtt protocol)  
Username and password to access the web console are _artemis:simetraehcapa_

## Sample data from the sensor
To test the full flow you can append the following payload to the topic previosuly created (and configured on quarkus app)  
```json
{
    "sensor":"truck1",
    "pressure":1007.05,
    "temperature":28.60751343,
    "humidity":51.09419632,
    "gas_resistance":7.362,
    "altitude":51.83121109,
    "gps":[48.75608,2.302038],
    "CO2":421,
    "ppm":3
}
```

## Data caching
For persisting partial results (and state in case of failure) we will be using [Infinispan](https://infinispan.org/get-started/)  
We will be installing it using [Helm charts](https://infinispan.org/docs/helm-chart/main/helm-chart.html#installing-chart-command-line_install)  
You can find the chart values [here](infinispan-values.yaml)  
```
helm install infinispan -n default openshift-helm-charts/infinispan-infinispan --values infinispan-values.yaml
```

## Geo query
For the geo query functionality we are going to rely on [Redis](https://redis.io/commands/geosearch/)  
We will install the component using Helm charts with the following parameters:  

```
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
helm install my-redis -n default --set auth.password=password --set master.persistence.storageClass=kubevirt-hostpath-provisioner --set architecture=standalone --set replica.replicaCount=0 --set metrics.enabled=true bitnami/redis
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/code-with-quarkus-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.

## Related Guides

- Camel Log ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/log.html)): Log messages to the underlying logging mechanism
- Camel Core ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/core.html)): Camel core functionality and basic Camel languages: Constant, ExchangeProperty, Header, Ref, Simple and Tokenize
- Camel ActiveMQ ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/activemq.html)): Send messages to (or consume from) Apache ActiveMQ. This component extends the Camel JMS component
- Camel HTTP ([guide](https://camel.apache.org/camel-quarkus/latest/reference/extensions/http.html)): Send requests to external HTTP servers using Apache HTTP Client 4.x
