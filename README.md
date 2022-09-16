# Edge Open Demo Project

## Edge Gateway (based on RHEL with Microshift preinstalled)
To use the prebuilt image, create a VM using the provided ISO as installation disk found [here](ftp://ftpuser@65.21.88.32/aaf08b97-d2fd-4ba4-8729-048c24edd7e3-installer.iso)
As operating system select RHEL8.6, create a VM with the disk image as the ISO and make sure to select EUFI as boot loader, provide at least 4GB of RAM, choose automatic partioning
Username and password of are admin:password
After the installation Microshift should be running already, you can check with:
```
systemctl status microshift
```
You would need to install oc or kubectl to access the cluster
```
curl -O https://mirror.openshift.com/pub/openshift-v4/$(uname -m)/clients/ocp/stable/openshift-client-linux.tar.gz
sudo tar -xf openshift-client-linux.tar.gz -C /usr/local/bin oc kubectl
```
and copy the kubeconfig file
```
mkdir ~/.kube
sudo cat /var/lib/microshift/resources/kubeadmin/kubeconfig > ~/.kube/config
```
Now you should be able to access the microshift cluster


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
