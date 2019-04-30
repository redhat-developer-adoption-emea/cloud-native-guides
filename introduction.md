## Introduction

#### What is the Cloud Native Developer Series Workshop?
This workshop is a series of hands-on-labs which are designed to familiarize participants with cloud-native concepts
and give them a taste of using OpenShift and OpenShift Application Runtimes (Spring Boot, Thorntail/WildFly Swarm, Vert.x, and Node.js)
for building and managing cloud-native applications.

Today you are going to create, deploy and debug a containerized polyglot microservices application called CoolStore (retail online shop).

![CoolStore Shop]({% image_path coolstore-web.png %}){:width="500px"}

#### What is the architecture of this retail application?

Several individual microservices and infrastructure components make up the CoolStore application:

* **Catalog Service** – Java application serving products and prices for retail products.
    * Based on [Spring Boot]({{ SPRINGBOOT_URL }}) and JBoss technologies.
    * Running on JBoss Web Server (Tomcat).
* **Inventory Service** – Java EE application serving inventory and availability data for retail products
    * Based on [WildFly Swarm/Thorntail]({{ WILDFLYSWARM_URL }})
    * Running on JBoss EAP 7.
* **API Gateway** – Java EE application serving as an entry point/router/aggregator to the backend services (Catalog and Inventory APIs)
    * Based on [Eclipse Vert.x]({{ ECLIPSE_VERTX_URL }})
    * Running on JBoss EAP 7.
* **Web UI** – A frontend web user interface
    * Based on [AngularJS]({{ ANGULARJS_URL }}) and [PatternFly]({{ PATTERNFLY_URL }}).
    * Running in a [Node.js]({{ NODEJS_URL }}) container.


![API Gateway Pattern]({% image_path coolstore-arch.png %}){:width="500px"}

#### Why are you here?
*"Your mission, should you choose to accept it"* is to implement the Catalog service,
to deploy it as well as the Inventory service, the API Gateway service and the web user interface
and to investigate bugs and issues using remote debugging technique.
As always should you or any member of the workshop need support or get questions, the facilitator team is here to help you.<br/>
Don't worry! The instructions will not self-destruct in 5 seconds so **Good Luck!** And move on to the next lab.