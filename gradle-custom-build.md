## Custom Build with Gradle

What if you have to build your code using a tool (Gradle for instance) that is not part of the supported image to run your code (say OpenJDK)?

For these situations we can leverage a builder image to create an artifact (JAR, WAR, etc.) and then copy it to the official supported image.

~~~shell
CAVEAT: you need to know where the artifact is left in the builder image and where you need to place the artifact in the runtime image
~~~

In this lab you will create an alternative version of the inventory using Spring Boot and Gradle, instead of Wildfly and Maven.

#### Creating the builder

Let's check we're at the righ project.

~~~shell
$ oc project
Using project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} on server "https://192.168.64.13:8443".
~~~

Let's create a BuildConfig with a custom [S2I image](https://github.com/jorgemoralespou/s2i-java) by Jorge Morales.

~~~shell
$ oc new-build jorgemoralespou/s2i-java~https://github.com/redhat-developer-adoption-emea/cloud-native-labs#ocp-3.10 \
   --context-dir=/inventory-spring-boot-gradle --name=gradle-builder
~~~

Watch the logs

~~~shell
$ oc logs -f bc/builder
...
[INFO] Application jar file is located in /opt/openshift/app.jar
Pushing image 172.30.1.1:5000/coolstore/gradle-builder:latest ...
...
Pushed 9/11 layers, 94% complete
Pushed 10/11 layers, 100% complete
Pushed 11/11 layers, 100% complete
Push successful
~~~

As you can see the built artifact should be located at `/opt/openshift/app.jar`.

Now it's time to create a new 'chained' builder that is going to use a 
source image `--source-image`, a mount path `--source-image-path`, a runtime image `--docker-image` to build the runtime image and an inline Dockerfile `--dockerfile`. Pay special attention to the COPY task in the inline Dockerfile because it copies the artifact from the source image to the right location at the runtime image.

~~~shell
$ oc new-build --name=gradle-runtime \
   --docker-image=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift \
   --source-image=gradle-builder \
   --source-image-path=/opt/openshift/app.jar:. \
   --dockerfile=$'FROM registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift\nCOPY app.jar /deployments/app.jar'
~~~

Now, let's create a new application based on the image stream `gradle-runtime` we just created.

~~~shell
$ oc new-app gradle-runtime --name=gradle-inventory
$ oc expose svc/gradle-inventory
~~~

Finally let's test the new inventory service.

~~~shell
$ export INVENTORY_ROUTE=http://$(oc get route/gradle-inventory -o jsonpath='{.status.ingress[0].host}')
$ curl ${INVENTORY_ROUTE}/api/inventory/329299
{"itemId":"329299","quantity":35}
~~~
