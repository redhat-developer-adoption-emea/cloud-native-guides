## Service Resilience and Fault Tolerance

In this lab you will learn about how you can build service resilience and fault-tolerant into 
the applications both at the infrastructure level using OpenShift capabilities as well as 
at the application level using circuit breakers to prevent cascading failures when 
downstream dependencies fail.

#### Scaling Up Applications

Applications capacity for serving clients is bounded by the amount of computing power 
allocated to them and although it's possible to increase the computing power per instance, 
it's far easier to keep the application instances within reasonable sizes and 
instead add more instances to increase serving capacity. Traditionally, due to 
the stateful nature of most monolithic applications, increasing capacity had been achieved 
via scaling up the application server and the underlying virtual or physical machine by adding 
more cpu and memory (vertical scaling). Cloud-native apps however are stateless and can be 
easily scaled up by spinning up more application instances and load-balancing requests 
between those instances (horizontal scaling).

![Scaling Up vs Scaling Out]({% image_path fault-scale-up-vs-out.png %}){:width="500px"}

In previous labs, you learned how to build container images from your application code and 
deploy them on OpenShift. Container images on OpenShift follow the 
[immutable server](https://martinfowler.com/bliki/ImmutableServer.html) pattern which guarantees 
your application instances will always starts from a known well-configured state and makes 
deploying instances a repeatable practice. Immutable server pattern simplifies scaling out 
application instances to starting a new instance which is guaranteed to be identical to the 
existing instances and adding it to the load-balancer.

Now, let's use the `oc scale` command to scale up the Web UI pod in the CoolStore retail 
application to 2 instances. In OpenShift, deployment config is responsible for starting the 
application pods and ensuring the specified number of instances for each application pod 
is running. Therefore the number of pods you want to scale to should be defined on the 
deployment config.

> You can scale pods up and down via the OpenShift Web Console by clicking on the up and 
> down arrows on the right side of each pods blue circle.

First, get list of deployment configs available in the project.

~~~shell
$ oc project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
$ oc get dc 

NAME        REVISION   DESIRED   CURRENT   TRIGGERED BY
inventory   1          1         1         config,image(inventory:latest)
~~~

And then, scale the `inventory` deployment config to 2 pods:

~~~shell
$ oc scale dc/inventory --replicas=2
~~~

The `--replicas` option specified the number of Web UI pods that should be running. If you look at the OpenShift Web Console, you can see a new pod is being started for the Web UI and as soon as the health probes pass, it will be automatically added to the load-balancer.

TODO <= change image

![Scaling Up Pods]({% image_path fault-scale-up.png %}){:width="740px"}

You can verify that the new pod is added to the load balancer by checking the details of the Web UI service object:

~~~shell
$ oc describe svc/inventory

...
Endpoints:              10.129.0.146:8080,10.129.0.232:8080
...
~~~

`Endpoints` shows the IPs of the 2 pods that the load-balancer is sending traffic to.

> The load-balancer by default, sends the client to the same pod on consequent requests. The [load-balancing strategy](https://docs.openshift.com/container-platform/3.5/architecture/core_concepts/routes.html#load-balancing) can be specified using an annotation on the route object. Run the following to change the load-balancing 
> strategy to round robin: 
> 
> $ oc annotate route/inventory haproxy.router.openshift.io/balance=roundrobin
>

#### Scaling Applications on Auto-pilot

Although scaling up and scaling down pods are automated and easy using OpenShift, however it still requires a person or a system to run a command or invoke an API call (to OpenShift REST API. Yup! there is a REST API for all OpenShift operations) to scale the applications. That in turn needs to be in response to some sort of increase to the application load and therefore the person or the system needs to be aware of how much load the application is handling at all times to make the scaling decision.

OpenShift automates this aspect of scaling as well via automatically scaling the application pods up and down within a specified min and max boundary based on the container metrics such as cpu and memory consumption. In that case, if there is a surge of users visiting the CoolStore online shop due to holiday season coming up or a good deal on a product, OpenShift would automatically add more pods to handle the increase load on the application and after the load goes, the application is automatically scaled down to free up compute resources.

In order to define auto-scaling for a pod, we should first define how much cpu and memory a pod is allowed to consume which will act as a guideline for OpenShift to know when to scale the pod up or down. Since the deployment config is used when starting the application pods, the application pod resource (cpu and memory) containers should also be defined on the deployment config.

When allocating compute resources to application pods, each container may specify a *request* and a *limit*value each for CPU and memory. The 
[*request*]({{OPENSHIFT_DOCS_BASE}}/dev_guide/compute_resources.html#dev-memory-requests) 
values define how much resource should be dedicated to an application pod so that it can run. It's the minimum resources needed in other words. The 
[*limit*]({{OPENSHIFT_DOCS_BASE}}/dev_guide/compute_resources.html#dev-memory-limits) values defines how much resource an application pod is allowed to consume, if there is more resources on the node available than what the pod has requested. This is to allow various quality of service tiers with regards to compute resources. You can read more about these quality of service tiers in [OpenShift Documentation]({{OPENSHIFT_DOCS_BASE}}/dev_guide/compute_resources.html#quality-of-service-tiers).

Set the following resource constraints on the Web UI pod:

* Memory Request: 256 MB
* Memory Limit: 512 MB
* CPU Request: 200 millicore
* CPU Limit: 300 millicore

> CPU is measured in units called millicores. Each node in a cluster inspects the operating system to determine the amount of CPU cores on the node, then multiplies that value by 1000 to express its total capacity. For example, if a node has 2 cores, the node’s CPU capacity would be represented as 2000m. If you wanted to use 1/10 of a single core, it would be represented as 100m. Memory is measured in bytes and is specified with [SI suffices]({{OPENSHIFT_DOCS_BASE}}/dev_guide/compute_resources.html#dev-compute-resources) (E, P, T, G, M, K) or their power-of-two-equivalents (Ei, Pi, Ti, Gi, Mi, Ki).

~~~shell
$ oc set resources dc/inventory --limits=cpu=400m,memory=512Mi --requests=cpu=200m,memory=256Mi

deploymentconfig "inventory" resource requirements updated
~~~

> You can also use the OpenShift Web Console by clicking on **Applications** >> **Deployments** within the **{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}** project. Click then on **web** and from the **Actions** menu on the top-right, choose **Edit Resource Limits**.

The pods get restarted automatically setting the new resource limits in effect. Now you can define an autoscaler using `oc autoscale` command to scale the Web UI pods up to 5 instances whenever the CPU consumption passes 50% utilization:

> You can configure an autoscaler using OpenShift Web Console by clicking on **Applications** >> **Deployments** within the **{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}** project. Click then on **web** and from the **Actions** menu on the top-right, choose **Edit Autoscaler**.

~~~shell
$ oc autoscale dc/inventory --min 1 --max 5 --cpu-percent=40

deploymentconfig "inventory" autoscaled
~~~

All set! Now the Inventory can scale automatically to multiple instances if the load on the Inventory increases. You can verify that using for example `siege` the [http load testing and benchmarking utility](https://www.joedog.org/siege-manual/). Let's deploy the `siege` container image from [Docker Hub](https://hub.docker.com/r/siamaksade/siege/) as a [Kubernetes job](https://docs.openshift.com/container-platform/3.10/dev_guide/jobs.html) and 
generate some load on the Web UI:

~~~shell
oc run inventory-load --restart='OnFailure' --image=siamaksade/siege -- -c80 -d2 -t5M  http://inventory:8080/
~~~

In the above, `--image` specified which container image should be deployed. OpenShift first looks in the internal image registry and then in defined upstream registries ([Red Hat Container Catalog](https://access.redhat.com/search/#/container-images) and [Docker Hub](https://hub.docker.com) by default) to find and pull this image. After `--`, you can override the container entry point to whatever command you want to run in that container.

As the load is generated, you will notice that it will create a spike in the 
Web UI cpu usage and trigger the autoscaler to scale the Web UI container to 5 pods (as configured on the deployment config) to cope with the load.

> Depending on the resources available on the OpenShift cluster, the pod might scale to fewer than 5 pods to handle the extra load. You can generate more load load by specifying a higher number of concurrent requests `-c80` flag. Just make sure to remove the existing `inventory-load` job first (see if you can find out how!). 


TODO <= change next image

![Web UI Automatically Scaled]({% image_path fault-autoscale-web.gif %}){:width="740px"}

You can see the aggregated cpu metrics graph of all 5 Web UI pods by going to the OpenShift Web Console and clicking on **Monitoring** and then the arrow (**>**) on the left side of **web-n** under **Deployments**.

TODO <= change next image

![Web UI Aggregated CPU Metrics]({% image_path fault-autoscale-metrics.png %}){:width="740px"}

When the load on Inventory disappears, after a while OpenShift scales the Inventory pods down to the minimum or whatever this needed to cope with the load at that point.

#### Self-healing Failed Application Pods

We looked at how to build more resilience into the applications through scaling in the previous sections. In this section, you will learn how to recover application pods when failures happen. In fact, you don't need to do anything because OpenShift automatically recovers failed pods when pods are not feeling healthy. The healthiness of application pods is determined via the [health probes]({{OPENSHIFT_DOCS_BASE}}/dev_guide/application_health.html#container-health-checks-using-probes) which was discussed in the previous labs.

There are three auto-healing scenarios that OpenShift handles automatically:

* Application Pod Temporary Failure: when an application pod fails and does not pass its [liveness health probe]({{OPENSHIFT_DOCS_BASE}}/dev_guide/application_health.html#container-health-checks-using-probes),  
OpenShift restarts the pod in order to give the application a chance to recover and start functioning again. Issues such as deadlocks, memory leaks, network disturbance and more are all examples of issues that can most likely be resolved by restarting the application despite the potential bug remaining in the application.

* Application Pod Permanent Failure: when an application pod fails and does not pass its [readiness health probe]({{OPENSHIFT_DOCS_BASE}}/dev_guide/application_health.html#container-health-checks-using-probes), 
it signals that the failure is more severe and restart is unlikely to help to mitigate the issue. OpenShift then removes the application pod from the load-balancer to prevent sending traffic to it.

* Application Pod Removal: if an instance of the application pods gets removed, OpenShift automatically starts new identical application pods based on the same container image and configuration so that the specified number of instances are running at all times. An example of a removed pod is when an entire node (virtual or physical machine) crashes and is removed from the cluster.

> OpenShift is quite orderly in this regard and if extra instances of the application pod would start running, it would kill the extra pods so that the number of running instances matches what is configured on the deployment config.

All of the above comes out-of-the-box and don't need any extra configuration. Remove the Inventory pod to verify how OpenShift starts the pod again. First, check the Inventory pod that is running:

~~~shell
$ oc get pods -l deploymentconfig=inventory

NAME              READY     STATUS    RESTARTS   AGE
inventory-3-xf111   1/1       Running   0          42m
inventory-3-mfr17   1/1       Running   0          42m
~~~

The `-l` options tells the command to list pods that have the `deploymentconfig=inventory` label assigned to them. You can see pods labels using `oc get pods --show-labels` command.

Delete the Inventory pod. 

~~~shell
oc delete pods -l deploymentconfig=inventory
~~~

You need to be fast for this one! List the Inventory pods again immediately:

~~~shell
$ oc get pods -l deploymentconfig=inventory

NAME              READY     STATUS              RESTARTS   AGE
inventory-3-5dx5d   0/1       ContainerCreating   0          1s
inventory-3-xf111   0/1       Terminating         0          4m
...
~~~

As the Inventory pod is being deleted, OpenShift notices the lack of 1 pod and starts a new Inventory pod automatically.

Well done! Let's move on to the next.
