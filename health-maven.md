## Monitoring Application Health 

In this lab we will learn how to monitor application health using OpenShift health probes and how you can see container resource consumption using metrics.

####  Health Probes

When building microservices, monitoring becomes of extreme importance to make sure all services are running at all times, and when they don't there are automatic actions triggered to rectify the issues. 

OpenShift, using Kubernetes health probes, offers a solution for monitoring application health and trying to automatically heal faulty containers through restarting them to fix issues such as a deadlock in the application which can be resolved by restarting the container. Restarting a container in such a state can help to make the application more available despite bugs.

Furthermore, there are of course a category of issues that can't be resolved by restarting the container. 
In those scenarios, OpenShift would remove the faulty container from the built-in load-balancer and send traffic only to the healthy containers that remain.

There are two types of health probes available in OpenShift: [liveness probes and readiness probes]({{OPENSHIFT_DOCS_BASE}}/dev_guide/application_health.html#container-health-checks-using-probes). *Liveness probes* are to know when to restart a container and *readiness probes* to know when a Container is ready to start accepting traffic.

Health probes also provide crucial benefits when automating deployments with practices like rolling updates in order to remove downtime during deployments. A readiness health probe would signal OpenShift when to switch traffic from the old version of the container to the new version so that the users don't get affected during deployments.

There are [three ways to define a health probe]({{OPENSHIFT_DOCS_BASE}}/dev_guide application_health.html#container-health-checks-using-probes) for a container:

* **HTTP Checks:** healthiness of the container is determined based on the response code of an HTTP endpoint. Anything between 200 and 399 is considered success. A HTTP check is ideal for applications that return HTTP status codes when completely initialized.

* **Container Execution Checks:** a specified command is executed inside the container and the healthiness is determined based on the return value (0 is success). 

* **TCP Socket Checks:** a socket is opened on a specified port to the container and it's considered healthy only if the check can establish a connection. TCP socket check is ideal for applications that do not start listening until initialization is complete.
 
Let's add health probes to our Inventory microservice.

####  Explore Health REST Endpoints

Spring Boot, WildFly Swarm and Vert.x all provide out-of-the-box support for creating RESTful endpoints that provide details on the health of the application. These endpoints by default provide basic data about the service however they all provide a way to customize the health data and add more meaningful information (e.g. database connection health, backoffice system availability, etc).

[Spring Boot Actuator](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#production-ready) is a sub-project of Spring Boot which adds health and management HTTP endpoints to the application. Enabling Spring Boot Actuator is done via adding `org.springframework.boot:spring-boot-starter-actuator` dependency to the Maven project dependencies which was already done when you added Prometheus metrics to the generated code.

> **NOTE:** You can check that the next dependency is in place in `pom.xml`

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
    <version>2.1.3.RELEASE</version>
</dependency>
~~~

Verify that the health endpoint works for the Inventory service using `curl`, replacing `{{INVENTORY_ROUTE_HOST}}` 
with the Inventory route url:

> **TIP:** Remember how to find out the route urls? Try `oc get route inventory-s2i` 

~~~shell
$ oc get route -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} | grep inventory
inventory-s2i   inventory-s2i-{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}.apps.serverless-d50b.openshiftworkshop.com             inventory-s2i   8080-tcp                 None
~~~

~~~shell
$ curl http://{{INVENTORY_ROUTE_HOST}}/health

{"status":"UP","diskSpace":{"status":"UP","total":3209691136,"free":2667175936,"threshold":10485760},"db":{"status":"UP","database":"H2","hello":1}}
~~~

Last but not least, although you can build more sophisticated health endpoints for the Web UI as well, you 
can use the root context ("/") of the Web UI in this lab to verify it's up and running.

####  Monitoring Inventory Service Health

Health probes are defined on the deployment config for each pod and can be added using OpenShift Web Console or OpenShift CLI. You will try both in this lab.

Like mentioned, health probes are defined on a deployment config for each pod. Review the available deployment configs in the project. 

~~~shell
$ oc get dc

NAME            REVISION   DESIRED   CURRENT   TRIGGERED BY
inventory-s2i   1          1         1         config,image(inventory:latest)
~~~

> `dc` stands for deployment config

Add a liveness probe on the `inventory-s2i` deployment config using `oc set probe`:

~~~shell
$ oc set probe dc/inventory-s2i --liveness --initial-delay-seconds=30 --failure-threshold=3 --get-url=http://:8080/health
~~~

> OpenShift automates deployments using [deployment triggers]({{OPENSHIFT_DOCS_BASE}}/dev_guide/deployments/basic_deployment_operations.html#triggers) that react to changes to the container image or configuration. Therefore, as soon as you define the probe, OpenShift automatically redeploys the Inventory pod using the new configuration including the liveness probe. 

The `--get-url` defines the HTTP endpoint to use for check the liveness of the container. The `\http://:8080` syntax is a convenient way to define the endpoint without having to worry about the hostname for the running container. 

> It is possible to customize the probes even further using for example `--initial-delay-seconds` to specify how long to wait after the container starts and before to begin checking the probes. Run `oc set probe --help` to get a list of all available options.

Add a readiness probe on the inventory deployment config using the same `/health` endpoint that you used for the liveness probe.

> It's recommended to have separate endpoints for readiness and liveness to indicate to OpenShift when to restart the container and when to leave it alone and remove it from the load-balancer so that an administrator would  manually investigate the issue. 

~~~shell
$ oc set probe dc/inventory-s2i --readiness --initial-delay-seconds=30 --failure-threshold=3 --get-url=http://:8080/health 
~~~

Viola! OpenShift automatically restarts the Inventory pod and as soon as the 
health probes succeed, it is ready to receive traffic. 

> Fabric8 Maven Plugin can also be configured to automatically set the health probes when running `fabric8:deploy` goal. Read more on [Fabric8 docs](https://maven.fabric8.io/#enrichers) under [Spring Boot](https://maven.fabric8.io/#f8-spring-boot-health-check), [WildFly Swarm](https://maven.fabric8.io/#f8-wildfly-swarm-health-check) and [Eclipse Vert.x](https://maven.fabric8.io/#f8-vertx-health-check).

#### Monitoring Metrics TODO

Metrics are another important aspect of monitoring applications which is required in order to gain visibility into how the application behaves and particularly in identifying issues.

OpenShift provides container metrics out-of-the-box and displays how much memory, cpu and network each container has been consuming over time. In the project overview, you can see three charts near each pod that shows the resource consumption by that pod.

![Container Metrics]({% image_path health-metrics-brief.png %}){:width="740px"}

Click on any of the pods (blue circle) which takes you to the pod details. Click on the **Metrics** tab to see a more detailed view of the metrics charts.

![Container Metrics]({% image_path health-metrics-detailed.png %}){:width="900px"}

Well done! You are ready to move on to the next lab.
