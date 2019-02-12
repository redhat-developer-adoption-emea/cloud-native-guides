## Creating a Prometheus instance by using the Operator Lifecycle Manager

This is lab will guide you to create an instance of Prometheus by using the [Prometheus Operator](https://coreos.com/operators/prometheus/docs/latest/user-guides/getting-started.html).

> This lab is meant to be run by the instructor or yourself if your user has been provided with the cluster administrator role.

#### What is the Prometheus Operator?

Operators were introduced by CoreOS as a class of software that operates other software, putting operational knowledge collected by humans into software. For further information around the Operator Framework please go [here](https://github.com/operator-framework/getting-started).

The Prometheus Operator serves to make running Prometheus on top of Kubernetes as easy as possible, while preserving Kubernetes-native configuration options.

#### The Operator Lifecycle Manager

The Operator Framework (currently in Technology Preview phase) installs the Operator Lifecycle Manager (OLM), which aids cluster administrators in installing, upgrading, and granting access to Operators running on their OpenShift Container Platform cluster.

The OpenShift Container Platform web console has been is also updated for cluster administrators to install Operators, as well as grant specific projects access to use the catalog of Operators available on the cluster.

One of the Red Hat Supported Operators is the one we need for this lab, this means you don't need to install the operator itself but to use it. As any other operator there are a set of objects (CRDs) we need to create to tell the operator how we want to install and operate Prometheus.

These are the objects we'll need to create:

* Prometheus 
* ServiceMonitor
* AlertManager

The next image shows how they're related. For further details please go [here](https://coreos.com/operators/prometheus/docs/latest/user-guides/getting-started.html)

![Prometheus Operator Architecture]({% image_path prometheus-operator-architecture.png %}){:width="740px"}

#### Preprequisites

In order to follow this lab you'll need:

* A u ser who has been granted the `cluster-admin` role
* Create a project to install Prometheus

The next command lines show how to grant a user the `cluster-admin` role and create a project.

~~~shell
oc adm policy add-cluster-role-to-user cluster-admin <user_name>
oc new-project monitoring
~~~

#### End result of the lab

The aim of this lab is to deploy the following architecture. 

> * As you can see we need to define a **Prometheus** server 'linked' to a set of **ServiceMonitors**  through a `serviceMonitorSelector` rule, in this case we're interested on ServiceMonitors containing label `k8s-app` no matter which value it contains.
> * Additionally we'll define a **ServiceMonitor** containg the required label `k8s-app` which in its turn we'll trigger the scanning of Services according to the rule defined in the `selector` section (matches label **team** with value **backend**). 
> * Finally **port** property in section `endpoints` of our **ServiceMonitor** should match the **port** name defined in our target **Service** objects.

![Prometheus Operator End Result]({% image_path prometheus-operator-architecture-lab.png %}){:width="740px"}

#### Create a Prometheus subscription

Please go to the OpenShift Web [console]({{OPENSHIFT_CONSOLE_URL}})

![Prometheus Operator Deployment 1]({% image_path prometheus-operator-deploy-1.png %}){:width="740px"}

Then go to the Cluster Console and open the `Operators➡Catalog Resources` menu on the left. There we'll create a `Subscription` which the way we manage the Prometheus Operator itself, not the servers.

![Prometheus Operator Deployment 2]({% image_path prometheus-operator-deploy-2.png %}){:width="740px"}

Make sure that  the `monitoring` project we created before is selected before proceeding! 

![Prometheus Operator Deployment 3]({% image_path prometheus-operator-deploy-3.png %}){:width="740px"}

Now it's time to create the Prometheus Operator subscription. Please, scroll down and click on the `Create Subscription` button close to the Prometheus Operator.

![Prometheus Operator Deployment 4]({% image_path prometheus-operator-deploy-4.png %}){:width="740px"}

Now you should be presented with a default/example subscription descriptor, pay attention to the namespace, it should be `monitoring`. Once checked the namespace please click on `Create`

![Prometheus Operator Deployment 5]({% image_path prometheus-operator-deploy-5.png %}){:width="740px"}

Example subscription:

~~~
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  generateName: prometheus-
  namespace: monitoring
spec:
  source: rh-operators
  name: prometheus
  startingCSV: prometheusoperator.0.22.2
  channel: preview
~~~

If everything goes as expected you should see something similar to this. You should see the upgrade status as `Up to date`, if that is the case click on the link pointed by the arrow which should take you to the `Cluster Service Versions` area (menu on the left).

![Prometheus Operator Deployment 6]({% image_path prometheus-operator-deploy-6.png %}){:width="740px"}

You should be able to see the description and links to documentation of the Prometheus Operator along with a set of `Create New` commands, signaled with a red arrow.

![Prometheus Operator Deployment 7]({% image_path prometheus-operator-deploy-7.png %}){:width="740px"}

Good job, you have deployed the operator in project `monitoring`, in fact if you go to the OpenShift console to project `monitor` you should see one instance of the prometeus-operator as in the next picture.

![Prometheus Operator Deployment 8]({% image_path prometheus-operator-deploy-8.png %}){:width="740px"}

Now let's proceed with the deployment of the Prometheus server.

##### Deployment of the Prometheus Server

Go back to the `Cluster Console` and click on the `Create New` button and choose `Prometheus`

![Prometheus Operator Deployment 9]({% image_path prometheus-operator-deploy-9.png %}){:width="740px"}

Next screen shows an example descriptor of a Prometheus server, go aheade and change `metadata-➡name` to server as in the image and click `Create`.

> Pay attention to section `spec➡serviceMonitorSelector`. There is where we define the match expression to select which Service Monitors we're interested in. In this case we want Service Monitors with a label called `key`
> Also pay attention to `spec➡replicas`, if you go to the OpenShift Application Console you'll find a StatefulSet called prometheus**-server** with exactly 2 replicas

![Prometheus Operator Deployment 10]({% image_path prometheus-operator-deploy-10.png %}){:width="740px"}

~~~yaml
apiVersion: monitoring.coreos.com/v1
kind: Prometheus
metadata:
  name: server
  labels:
    prometheus: k8s
  namespace: monitoring
spec:
  replicas: 2
  version: v2.3.2
  serviceAccountName: prometheus-k8s
  securityContext: {}
  serviceMonitorSelector:
    matchExpressions:
      - key: k8s-app
        operator: Exists
  ruleSelector:
    matchLabels:
      role: prometheus-rulefiles
      prometheus: k8s
  alerting:
    alertmanagers:
      - namespace: monitoring
        name: alertmanager-main
        port: web
~~~

##### Deploying a test application with monitoring enabled

We've borrowed the following example from the [**Getting Started Guide**](https://coreos.com/operators/prometheus/docs/latest/user-guides/getting-started.html) of the **Prometheus Operator**

Please follow the next steps to deploy a test application (3 pods) that exposes Prometheus metrics along with a Service that balances requests to the pods.

Let's create a project for our application.

~~~shell
oc new-project monitored-apps
~~~

Let's deploy the test aplication.

~~~shell
$ cat << EOF | oc create -n "monitored-apps" -f -
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: example-app
spec:
  replicas: 3
  template:
    metadata:
      labels:
        app: example-app
    spec:
      containers:
      - name: example-app
        image: fabxc/instrumented_app
        ports:
        - name: web
          containerPort: 8080
EOF
~~~

Let's check the status of those 3 pods.

~~~shell
$ oc get pod -n "monitored-apps"
NAME                        READY     STATUS    RESTARTS   AGE
example-app-94c8bc8-jq5cr   1/1       Running   0          30s
example-app-94c8bc8-phfrv   1/1       Running   0          30s
example-app-94c8bc8-vfgr7   1/1       Running   0          30s
~~~

Now let's create a Service object to balance to these pods.

> Pay attention to `spec➡port➡name` as we explained before should match the value of `metadata➡endpoints➡port` in the ServiceMonitor

~~~
$ cat << EOF | oc create -n "monitored-apps" -f -
kind: Service
apiVersion: v1
metadata:
  name: example-app
  labels:
    app: example-app
    team: backend
spec:
  selector:
    app: example-app
  ports:
  - name: web
    port: 8080
EOF
~~~

#### Let's create a ServiceMonitor to scan our test Service

Please go to the Cluster Console to the `Operators➡Cluster Service Versions` area. And click on `Create New` and select Service Monitor.

> **Remember** that project should be `monitoring`

![Create Service Monitor 1]({% image_path prometheus-operator-service-monitor-1.png %}){:width="740px"}

The next descriptor will deploy a ServiceMonitor which is compliant with the rule we defined in our Prometheus object, namely: having a label named `k8s-app`

> **Attention:** we are creating the **ServiceMonitor** *in the same namespace* of the **Prometheus** object.

![Create Service Monitor 2]({% image_path prometheus-operator-service-monitor-2.png %}){:width="740px"}

~~~yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: backend-monitor
  labels:
    k8s-app: backend-monitor
  namespace: monitoring
spec:
  namespaceSelector:
    any: true
  selector:
    matchLabels:
      team: backend
  endpoints:
    - interval: 30s
      port: web
~~~

> TIP: **namespaceSelector** could also define exactly which namespaces you want to discovered targets from

~~~yaml
spec:
  namespaceSelector:
    matchNames:
      - monitored-apps
~~~

#### Checking configuration

So far we have created a Prometheus server and a ServiceMonitor that points to a Service. Now we should check if everything is fine or not.

We can do this by checking Prometheus server logs, but before we do that we need to locate one of the pods, the next command will help us here.

~~~shell
$ oc get pods -n monitoring
NAME                                   READY     STATUS    RESTARTS   AGE
prometheus-operator-7fccbd7c74-48m6v   1/1       Running   0          16h
prometheus-server-0                    3/3       Running   1          3h
prometheus-server-1                    3/3       Running   1          3h
~~~

Now that we know the name of the pods we're looking for we can read the logs. Next command gets us the logs of container `prometheus` in one of the target pods.

~~~shell
$ oc logs prometheus-server-0 -c prometheus -n monitoring
...
level=error ts=2019-02-12T10:57:12.739199828Z caller=main.go:218 component=k8s_client_runtime err="github.com/prometheus/prometheus/discovery/kubernetes/kubernetes.go:289: Failed to list *v1.Pod: pods is forbidden: User \"system:serviceaccount:monitoring:prometheus-k8s\" cannot list pods in the namespace \"monitored-apps\": no RBAC policy matched"
level=error ts=2019-02-12T10:57:12.739190937Z caller=main.go:218 component=k8s_client_runtime err="github.com/prometheus/prometheus/discovery/kubernetes/kubernetes.go:288: Failed to list *v1.Service: services is forbidden: User \"system:serviceaccount:monitoring:prometheus-k8s\" cannot list services in the namespace \"monitored-apps\": no RBAC policy matched"
level=error ts=2019-02-12T10:57:12.73929972Z caller=main.go:218 component=k8s_client_runtime err="github.com/prometheus/prometheus/discovery/kubernetes/kubernetes.go:287: Failed to list *v1.Endpoints: endpoints is forbidden: User \"system:serviceaccount:monitoring:prometheus-k8s\" cannot list endpoints in the namespace \"monitored-apps\": no RBAC policy matched"
~~~

Well... something is not ok... apparently the problem has to do with permissions over namespace `monitored-apps`.

> **system:serviceaccount:monitoring:prometheus-k8s** cannot list endpoints in the namespace **"monitored-apps"**

So what we have to do is grant those required permissions (`view`) to the Service Account created by the operator and used by Prometheus.

We could grant a cluter-role to the service account, this way it can monitor any namespace, as in the next command.

~~~shell
oc adm policy add-cluster-role-to-user view system:serviceaccount:monitoring:prometheus-k8s
~~~

Or we can add permissions in a namespace basis as in the next one.

~~~shell
oc adm policy add-role-to-user view system:serviceaccount:monitoring:prometheus-k8s -n monitored-apps
~~~

Once you run one of the two versions errors logs should stop appearing.

Further checking... would involve using the Prometheus console... in order to do so we need first expose the Service as in the next command.

~~~shell
$ oc get svc -n monitoring
NAME                  TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)    AGE
prometheus-operated   ClusterIP   None         <none>        9090/TCP   13h
$ oc expose svc/prometheus-operated -n monitoring
route.route.openshift.io/prometheus-operated exposed
~~~

Now please open the url returned by the next command and navigate to `Status➡Targets`

~~~shell
$ oc get route -n monitoring
NAME                  HOST/PORT                                                                   PATH      SERVICES              PORT      TERMINATION   WILDCARD
prometheus-operated   prometheus-operated-monitoring.apps.serverless-8d48.openshiftworkshop.com             prometheus-operated   web                     None
~~~

You should see something like this. There are three targets, one per pod.

![Prometheus Targets]({% image_path prometheus-operator-targets-view.png %}){:width="740px"}

Now if you navigate to `Status➡Configuration` you should be able to see that there's a scrape_config entry per ServiceMonitor object, in our case we only have one, called `backnd-monitor`, the generated scrape-config name `monitoring/backend-monitor/0`

![Prometheus Targets]({% image_path prometheus-operator-configuration-view.png %}){:width="740px"}

#### See it in action

Now that we're sure that our target service is being monitored we could go and see some graphs. To do so, navigate to `Graph` and start typing `codelab` in the `Expression...` textfield. Then choose one of the available metrics, for instance `codelab_api_http_requests_in_progress` ...

![Prometheus Graph]({% image_path prometheus-operator-graph-view-1.png %}){:width="740px"}

...and click on tab `Graph`.

![Prometheus Graph]({% image_path prometheus-operator-graph-view-2.png %}){:width="740px"}

Well done! You are ready to move on to the next lab.