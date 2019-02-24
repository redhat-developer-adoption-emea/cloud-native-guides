## Adding Metrics to Spring Boot + Gradle with Prometheus
In this lab you're going to add metrics to the Inventory API created in the previous lab. We're going to use the [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#production-ready) along with Prometheus to expose business (and some built in low level) metrics.

For this lab, Prometheus has already been provisioned, ask your instructor about how you can do it yourself with the [Operator Framework](https://docs.openshift.com/container-platform/3.11/install_config/installing-operator-framework.html).

> **Prometheus** is an open-source systems monitoring and alerting toolkit originally built at [SoundCloud](http://soundcloud.com/). Since its inception in 2012, many companies and organizations have adopted Prometheus, and the project has a very active developer and user community. It is now a standalone open source project and maintained independently of any company. To emphasize this, and to clarify the project's governance structure, Prometheus joined the [Cloud Native Computing Foundation](https://cncf.io/) in 2016 as the second hosted project, after Kubernetes.

Prometheus will expect your API to expose metrics in a certain way in a given endpoint `/metrics` or like in this case `/actuator/prometheus`.

> Check you're still in folder **`inventory-spring-boot-gradle/inventory`** before proceeding.

#### Adding actuator and prometheus dependencies

To add Prometheus support through the Spring Boot Actuator please add the following dependencies after the mark `<<< OpenAPI (3)`, as in the next excerpt of `build.gradle`.

~~~java
...
dependencies {
	...
	// <<< OpenAPI (3)

	// >>> Prometheus Metrics (1)
	// https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-actuator-autoconfigure
	compile group: 'org.springframework.boot', name: 'spring-boot-actuator-autoconfigure', version: '2.1.3.RELEASE'
	// https://mvnrepository.com/artifact/io.micrometer/micrometer-registry-prometheus
	compile group: 'io.micrometer', name: 'micrometer-registry-prometheus', version: '1.1.3'
	// <<< Prometheus Metrics (1)
}
...
~~~

Now let's add some properties to setup the behaviour of the actuator. To do that please open the application properties file `./src/main/resources/application.properties `  and add these properties to enable generic metrics, enable Prometheus metrics and expose them.

~~~properties
...
# Metrics related configurations
management.endpoint.metrics.enabled=true
management.endpoints.web.exposure.include=*
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true
~~~

Now let's check that the actuator is working properly. Let's run (stop it before if it was already running) our service.

~~~shell
$ ./gradlew bootRun
...
2019-02-21 10:05:53.879  INFO 68526 --- [           main] c.r.c.inventory.OpenAPI2SpringBoot       : Started OpenAPI2SpringBoot in 3.439 seconds (JVM running for 3.85)
<=========----> 75% EXECUTING [38s]
> :bootRun
~~~

Now open another terminal window and check if the default actuator endpoint is working properly.

> **TIP:** Adding `| jq '.'` to make it easier to spot the 'prometheus' URL...
> 
> ~~~json
...
"prometheus": {
      "href": "http://localhost:8080/actuator/prometheus",
      "templated": false
},
...
~~~

~~~shell
$ curl http://localhost:8080/actuator | jq '.'
{
  "_links": {
    ...
    "prometheus": {
      "href": "http://localhost:8080/actuator/prometheus",
      "templated": false
    },
    ...
  }
}
~~~

Now you can try the URL that exposes metrics as Prometheus expects them.

~~~shell
$ curl http://localhost:8080/actuator/prometheus
# HELP process_start_time_seconds Start time of the process since unix epoch.
# TYPE process_start_time_seconds gauge
process_start_time_seconds 1.55073995008E9
# HELP tomcat_global_request_max_seconds  
# TYPE tomcat_global_request_max_seconds gauge
...
~~~

Well, so far, you have added Prometheus support but we haven't added our own "business" metrics, so let's do it now.

We're going to add a Counter to count how many hits are received per endpoint at the Inventory API. Please open interface `InventoryApi` at folder `./src/main/java/com/redhat/cloudnative/inventory/api`.

Add the following line to method `inventoryGet` in the first line.

> This line means, increment counter `api.http.requests.total` that translates to `api_http_requests_total` in Prometheus for labels: api, method and endpoint with the provided values: inventory, GET and /inventory

~~~java
Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory").increment();
~~~

Do the same with method `inventoryItemIdGet`, this time the line should be.

~~~java
Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory/" + itemId).increment();
~~~

Add the corresponding import.

~~~java
import io.micrometer.core.instrument.Metrics;
~~~

After applying these changes `InventoryApi.java` should look like this (not relevant code has been hidden).

~~~java
...
import io.micrometer.core.instrument.Metrics;
...
public interface InventoryApi {
    ...
    default ResponseEntity<List<InventoryItem>> inventoryGet() {
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory").increment();
        ...
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
    ...
    default ResponseEntity<InventoryItem> inventoryItemIdGet(@ApiParam(value = "",required=true) @PathVariable("itemId") String itemId) {
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory/" + itemId).increment();
        ...
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}
~~~

Alright, now depencies are in place, built in metrics are on and custom metrics too, so it's time to make these counters roll!

In one terminal let's run our code (restart if necessary).

~~~shell
$ ./gradlew bootRun
~~~

In another terminal window, please run the following requests to make our counters count.

> This set of requests should be counted as:
> 
> * /inventory -> 2
> * /inventory/12345 -> 1
> * /inventory/67890 -> 3


~~~shell
curl http://localhost:8080/api/inventory
curl http://localhost:8080/api/inventory
curl http://localhost:8080/api/inventory/12345
curl http://localhost:8080/api/inventory/67890
curl http://localhost:8080/api/inventory/67890
curl http://localhost:8080/api/inventory/67890
~~~

Now let's see what the Prometheus metrics say.

~~~shell
$ curl -s http://localhost:8080/actuator/prometheus | grep api_http
# HELP api_http_requests_total  
# TYPE api_http_requests_total counter
api_http_requests_total{api="inventory",endpoint="/inventory",method="GET",} 2.0
api_http_requests_total{api="inventory",endpoint="/inventory/12345",method="GET",} 1.0
api_http_requests_total{api="inventory",endpoint="/inventory/67890",method="GET",} 3.0
~~~

#### Commit, Push and Rebuild

Let's commit and push our changes.

~~~shell
$ git commit -a -m "Prometheus metrics on"
$ git push origin master
~~~

> Before we rebuild our image we're going to add a label to the Service that loadbalances to the pods where our image actually runs as containers. This label is needed for Prometheus to find and scrape (collect) the metrics we have exposed with our changes in code and configuration.
>
> **Please make sure project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` is in use** by running: `oc project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`

~~~shell
$  oc label svc inventory-s2i team=spring-boot-actuator
~~~ 

Finally let's rebuild the image (start a new build) which leads to the deployment of our new code.

> **Please make sure project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` is in use** by running: `oc project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`

~~~shell
$ oc start-build bc/inventory-s2i
~~~

You can either progress of the build in the web console or running this command.

~~~shell
$ oc logs -f bc/inventory-s2i
~~~

Once it has finished, the new image should be rolled out and our Prometheus-ready code should be live.

~~~shell
$ oc get route | grep inventory-s2i
inventory-s2i   inventory-s2i-coolstore-XX.apps.serverless-d50b.openshiftworkshop.com             inventory-s2i   8080-tcp                 None
~~~

You can make some requests to the inventory API and see how our Prometheus metrics look.

#### Seeing it at work

Ask your instructor for the URL where you can reach the instance of Prometheus we have prepared for you.

Once you have it, open a browser and point to it.

![Prometheus]({% image_path inventory-spring-boot-gradle-prometheus-1.png %}){:width="740px"}

Start typing `api_` in the text field where an autosuggest feature will help us finding a given metric. In this case we're interested in metric `api_http_requests_total`, so please select it as in the next picture.

![Prometheus]({% image_path inventory-spring-boot-gradle-prometheus-2.png %}){:width="740px"}

Now click on the `Execute` button and see some collected values and their dimensions:  **'api'**, **'method'** and **'endpoint'** (renamed as **'exported_endpoint'**), and some others automatically added, **'namespace'**, ...

> **Remember** to execute the Inventory API some times otherwise you want see any values.

![Prometheus]({% image_path inventory-spring-boot-gradle-prometheus-3.png %}){:width="740px"}

Finally click on `Graph` and you'll see a chart with all the data series.

![Prometheus]({% image_path inventory-spring-boot-gradle-prometheus-4.png %}){:width="740px"}

Well done! You are ready to move on to the next lab.
