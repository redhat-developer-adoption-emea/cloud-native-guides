## Alternative Inventory Service using Spring Boot + Gradle

In this lab you will learn about **how you can build microservices using** **Spring Boot + Gradle** and **Red Hat Openshift**. During this lab, you will use [**Open API Generator**](https://github.com/openapitools/openapi-generator), a code generator that can generate API client libraries, server stubs, documentation and configuration automatically given an [**Open API**](https://www.openapis.org/) Spec.

You will use a previously created Open API specification or you'll be given one by the instructor.

#### Preprequisites

In order to follow this lab you'll need:

* **Java 8**, for the Open API Generator. You can find it [here](https://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html)

#### Retrieving the API specification

If you have executed the API desing lab go to [API Curio](https://www.apicur.io/), log in as you did before and navigate to the Inventory API.

> If you haven't executed the previous lab the instructor will hand you the YAML file with the API specification

![apicur.io landing page]({% image_path inventory-api-apicurio-retrieve-yaml-01.png %}){:width="740px"}

Click on `View All APIs`

![apicur.io landing page]({% image_path inventory-api-apicurio-retrieve-yaml-02.png %}){:width="740px"}

Click on `Inventory API`.

![apicur.io landing page]({% image_path inventory-api-apicurio-retrieve-yaml-03.png %}){:width="300px"}

Click on the menu as in the picture, then click on `Download (YAML)`

![apicur.io landing page]({% image_path inventory-api-apicurio-retrieve-yaml-04.png %}){:width="400px"}

#### Generating the code

There are several ways to generate Spring Boot code and use Gradle, one way would be to use OpenAPI Generator CLI to generate a Maven project to later move it to Gradle, another way would be to use the OpenAPI Generator plugin for Gradle.

We're going to use the latter approach, but in order to use the mentioned Gradle plugin we need a seed (minimum) Gradle project.

So, please go to [Spring Initializr](https://start.spring.io/) and generate a Spring Boot bootstrap project for Gradle, as in the next picture.

> **Don't forget** to add the **Web** dependency

![Deploying on OCP]({% image_path inventory-spring-boot-gradle-bootstrap.png %}){:width="740px"}

Now, please create a folder where will unzip the contents of the bootstrap Gradle project.

~~~shell
$ mkdir inventory-spring-boot-gradle
$ cd inventory-spring-boot-gradle
$ unzip inventory.zip
~~~

Let's test there are no errors.

~~~shell
$ ./gradlew build

BUILD SUCCESSFUL in 4s
5 actionable tasks: 3 executed, 2 up-to-date
~~~

The OpenAPI Generator plugin will need to reach our API specification, so let's copy it to the resources folder.

~~~shell
$ cp ~/Downloads/Inventory\ API.yaml ./src/main/resources/inventory.yaml
~~~

##### Adapting build.gradle to use the OpenAPI Generator

Open the `build.gradle` with your favorite editor, it should look like this one.

~~~java
plugins {
	id 'org.springframework.boot' version '2.1.3.RELEASE'
	id 'java'
}

apply plugin: 'io.spring.dependency-management'

group = 'com.redhat.cloudnative'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '1.8'

repositories {
	mavenCentral()
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
~~~

Now have a look to the updated version that included the needed references and update your `build.gradle` file accordingly.

> **Pay attention to section OpenAPI (4)** where we set up the plugin to generate code in a certain way

~~~java
// >>> OpenAPI (1)
buildscript {
  repositories {
    mavenLocal()
    mavenCentral()
  }
  dependencies {
    classpath "org.openapitools:openapi-generator-gradle-plugin:3.3.4"
  }
}
// <<< OpenAPI (1)

plugins {
	id 'org.springframework.boot' version '2.1.3.RELEASE'
	id 'java'
}

apply plugin: 'io.spring.dependency-management'

// >>> OpenAPI (2)
apply plugin: 'org.openapi.generator'
// <<< OpenAPI (2) 

group = 'com.redhat.cloudnative'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '1.8'

repositories {
	mavenCentral()
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	
	// >>> OpenAPI (3)
	// https://mvnrepository.com/artifact/io.swagger/swagger-core
	compile group: 'io.swagger', name: 'swagger-core', version: '1.5.0'
	// https://mvnrepository.com/artifact/io.springfox/springfox-swagger-ui
	compile group: 'io.springfox', name: 'springfox-swagger-ui', version: '2.8.0'
	// https://mvnrepository.com/artifact/io.springfox/springfox-swagger2
	compile group: 'io.springfox', name: 'springfox-swagger2', version: '2.8.0'
	// <<< OpenAPI (3)
}

// >>> OpenAPI (4)
openApiGenerate{
	generatorName = "spring"
	inputSpec = "$rootDir/src/main/resources/inventory.yaml".toString()
	outputDir = "$rootDir".toString() 
	apiPackage = "com.redhat.cloudnative.inventory.api"
	invokerPackage = "com.redhat.cloudnative.inventory"
	modelPackage = "com.redhat.cloudnative.inventory.model" 
	modelFilesConstrainedTo = []
	configOptions = [
		dateLibrary: "java8"
	]
}
// <<< OpenAPI (4)
~~~

All this references are for generating and running the code, also by adding the OpenAPI Generator plugin a new set of tasks should be available, run the next command to see them.

~~~shell
$ ./gradlew tasks
> Task :tasks

------------------------------------------------------------
All tasks runnable from root project
------------------------------------------------------------

Application tasks
-----------------
bootRun - Runs this project as a Spring Boot application.
...
OpenAPI Tools tasks
-------------------
openApiGenerate - Generate code via Open API Tools Generator for Open API 2.0 or 3.x specification documents.
openApiGenerators - Lists generators available via Open API Generators.
openApiMeta - Generates a new generator to be consumed via Open API Generator.
openApiValidate - Validates an Open API 2.0 or 3.x specification document.
...
~~~

The whole purpose of this chapter is to generate code from the API specification we just designed. Well, in order to do that we have to run the `openApiGenerate` task. **But before we do we have to handle a mismatch between the specification generated by Apicurio and the specification expected by the OpenAPI Generator plugin**.

This mismatch has to do with the examples area in our YAML, in fact it has to do with the representation of those examples string vs object. Please open file `/src/main/resources/inventory.yaml` and apply the following changes.

> Error is in element:
> 
> * `paths→/inventory→get→responses→200→content→application/json→examples→AllItems→value`

~~~yaml
...
paths:
  /inventory:
    get:
      ...
      responses:
        200:
          ...
          content:
            application/json:
              ...
              examples:
                AllItems:
                  value: |-
                    [{"itemId":"329299","quantity":35},{"itemId":"329199","quantity":12},
                    {"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},
                    {"itemId":"165954","quantity":43},{"itemId":"444434","quantity":32},
                    {"itemId":"444435","quantity":53}]
...
~~~

> It should be like this:
> 
> * So from `value: |-` to `value:`

~~~yaml
...
paths:
  /inventory:
    get:
      ...
      responses:
        200:
          ...
          content:
            application/json:
              ...
              examples:
                AllItems:
                  value:
                    [{"itemId":"329299","quantity":35},{"itemId":"329199","quantity":12},
                    {"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},
                    {"itemId":"165954","quantity":43},{"itemId":"444434","quantity":32},
                    {"itemId":"444435","quantity":53}]
...
~~~

> Next errors are in elements:
> 
> * `paths→/inventory/{itemId}→get→responses→200→content→application/json→examples→OneItem→value`
> * `paths→/inventory/{itemId}→get→responses→404→content→application/json→examples→NotFoundError→value`

~~~yaml
...
paths:
...
  /inventory/{itemId}:
    get:
      ...
      responses:
        200:
          ...
          content:
            application/json:
              ...
              examples:
                OneItem:
                  value: '{"itemId":"329299","quantity":35}'
        404:
          ...
          content:
            application/json:
              ...
              examples:
                NotFoundError:
                  value: '{"code" : "404", "message" : "Item 53 was not found"}'
...
~~~

> It should be as follows:
> 
> * So from `value: '{"itemId":"329299","quantity":35}'` to `value: {"itemId":"329299","quantity":35}`
> * So from `value: '{"code" : "404", "message" : "Item 53 was not found"}'` to `value: {"code" : "404", "message" : "Item 53 was not found"}`

~~~yaml
...
paths:
...
  /inventory/{itemId}:
    get:
      ...
      responses:
        200:
          ...
          content:
            application/json:
              ...
              examples:
                OneItem:
                  value: {"itemId":"329299","quantity":35}
        404:
          ...
          content:
            application/json:
              ...
              examples:
                NotFoundError:
                  value: {"code" : "404", "message" : "Item 53 was not found"}
...
~~~


> Next errors are in elements:
> 
> * `components→schemas→InventoryItem→example`
> * `components→schemas→GenericError→example`

~~~yaml
...
components:
  schemas:
    InventoryItem:
      title: Root Type for InventoryItem
      description: The root of the InventoryItem type's schema.
      type: object
      properties:
        itemId:
          type: string
        quantity:
          format: int32
          type: integer
      example: '{"itemId":"329299","quantity":35}'
    GenericError:
      title: Root Type for GenericError
      description: Generic Error Object.
      type: object
      properties:
        code:
          type: string
        message:
          type: string
      example: '{"code" : "404", "message" : "Item 53 was not found"}'
~~~

> It should be as follows:
> 
> * So from `example: '{"itemId":"329299","quantity":35}'` to `example: {"itemId":"329299","quantity":35}`
> * So from `example: '{"code" : "404", "message" : "Item 53 was not found"}'` to `example: {"code" : "404", "message" : "Item 53 was not found"}`

~~~yaml
...
components:
  schemas:
    InventoryItem:
      title: Root Type for InventoryItem
      description: The root of the InventoryItem type's schema.
      type: object
      properties:
        itemId:
          type: string
        quantity:
          format: int32
          type: integer
      example: {"itemId":"329299","quantity":35}
    GenericError:
      title: Root Type for GenericError
      description: Generic Error Object.
      type: object
      properties:
        code:
          type: string
        message:
          type: string
      example: {"code" : "404", "message" : "Item 53 was not found"}
~~~

Now, once our YAML has been fixed, let's run the `openApiGenerate` task and see how it generates the code.

> You'll see a couple of warnings we could have avoided by adding operationId to each operation, don't worry they're harmless

~~~shell
$ ./gradlew openApiGenerate

> Task :openApiGenerate
Empty operationId found for path: get /inventory. Renamed to auto-generated operationId: inventoryGet
Empty operationId found for path: get /inventory/{itemId}. Renamed to auto-generated operationId: inventoryItemIdGet
Successfully generated code to .../inventory-spring-boot-gradle/inventory

BUILD SUCCESSFUL in 1s
1 actionable task: 1 executed
~~~

So, where did the generated code go? Well, because we have configured task `openApiGenerate` as follows, code should be Spring, and located in packages (main class/interface highlighted):

* `com.redhat.cloudnative.inventory` → **OpenAPI2SpringBoot**
*  `com.redhat.cloudnative.inventory.api` → **InventoryApi**
*  `com.redhat.cloudnative.inventory.model` → **GenericError** and **InventoryItem**

Please, open java interface `com.redhat.cloudnative.inventory.api.`**InventoryApi** and have a look to method `inventoryGet()`.

> You'll find interesting that in this generated implementation code we're copying an example as the response to any request. The problem here is that the example is not an array... we'll fix it later.
> 
> <small> `ApiUtil.setExampleResponse(request, "application/json", "{  \"itemId\" : \"329299\",  \"quantity\" : 35}");`</small> 
> 
> You'll also find interested that response is by default marked as 501 'Not Implemented'. 
> 
> <small> `return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);`</small> 

Java code excerpt method `inventoryGet()`.

~~~java
...
@ApiOperation(value = "Get all InventoryItems", nickname = "inventoryGet", notes = "Should return all elements as an array of InventoryItems or an empty array if there are none", response = InventoryItem.class, responseContainer = "List", tags={  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Should return an arry of InventoryItems", response = InventoryItem.class, responseContainer = "List") })
    @RequestMapping(value = "/inventory",
        produces = { "application/json" }, 
        method = RequestMethod.GET)
    default ResponseEntity<List<InventoryItem>> inventoryGet() {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    ApiUtil.setExampleResponse(request, "application/json", "{  \"itemId\" : \"329299\",  \"quantity\" : 35}");
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }
...
~~~

As you know, there's usually only one class annotated as `` but after generating the code we have two `InventoryApplication.java` and `OpenAPI2SpringBoot.java` both of them in path `./src/main/java/com/redhat/cloudnative/inventory`, to avoid running into issues, we're going to delete the class we no longer need.

~~~shell
$ rm ./src/main/java/com/redhat/cloudnative/inventory/InventoryApplication.java
~~~

Now we can run our brand new API implementation, and see if it works.

~~~shell
$ ./gradlew bootRun

> Task :bootRun

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v2.1.3.RELEASE)

2019-02-19 12:09:33.361  INFO 13358 --- [           main] c.r.c.inventory.InventoryApplication     : Starting InventoryApplication on cvicensa-mbp with PID 13358 (.../inventory-spring-boot-gradle/inventory/build/classes/java/main started by cvicensa in.../inventory-spring-boot-gradle/inventory)
2019-02-19 12:09:33.363  INFO 13358 --- [           main] c.r.c.inventory.InventoryApplication     : No active profile set, falling back to default profiles: default
2019-02-19 12:09:34.615  INFO 13358 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port(s): 8080 (http)
2019-02-19 12:09:34.643  INFO 13358 --- [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2019-02-19 12:09:34.644  INFO 13358 --- [           main] org.apache.catalina.core.StandardEngine  : Starting Servlet engine: [Apache Tomcat/9.0.16]
2019-02-19 12:09:34.653  INFO 13358 --- [           main] o.a.catalina.core.AprLifecycleListener   : The APR based Apache Tomcat Native library which allows optimal performance in production environments was not found on the java.library.path: [.../Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:.]
2019-02-19 12:09:34.737  INFO 13358 --- [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2019-02-19 12:09:34.738  INFO 13358 --- [           main] o.s.web.context.ContextLoader            : Root WebApplicationContext: initialization completed in 1339 ms
2019-02-19 12:09:35.145  INFO 13358 --- [           main] pertySourcedRequestMappingHandlerMapping : Mapped URL path [/api-docs] onto method [public org.springframework.http.ResponseEntity<springfox.documentation.spring.web.json.Json> springfox.documentation.swagger2.web.Swagger2Controller.getDocumentation(java.lang.String,javax.servlet.http.HttpServletRequest)]
2019-02-19 12:09:35.219  WARN 13358 --- [           main] uration$JodaDateTimeJacksonConfiguration : spring.jackson.date-format could not be used to configure formatting of Joda's DateTime. You may want to configure spring.jackson.joda-date-time-format as well.
2019-02-19 12:09:35.258  INFO 13358 --- [           main] o.s.s.concurrent.ThreadPoolTaskExecutor  : Initializing ExecutorService 'applicationTaskExecutor'
2019-02-19 12:09:35.383  INFO 13358 --- [           main] d.s.w.p.DocumentationPluginsBootstrapper : Context refreshed
2019-02-19 12:09:35.400  INFO 13358 --- [           main] d.s.w.p.DocumentationPluginsBootstrapper : Found 1 custom documentation plugin(s)
2019-02-19 12:09:35.428  INFO 13358 --- [           main] s.d.s.w.s.ApiListingReferenceScanner     : Scanning for api listing references
2019-02-19 12:09:35.580  INFO 13358 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8080 (http) with context path ''
2019-02-19 12:09:35.584  INFO 13358 --- [           main] c.r.c.inventory.InventoryApplication     : Started InventoryApplication in 2.579 seconds (JVM running for 2.91)
<=========----> 75% EXECUTING [9s]
> :bootRun

~~~

Now please open a browser and point to http://localhost:8080 you should see a user interface like this.

![Swagger UI]({% image_path inventory-spring-boot-gradle-swagger-ui-1.png %}){:width="740px"}

Click on the 'inventory-api-controller' area and on 'Models' you will see the following/

![Swagger UI]({% image_path inventory-spring-boot-gradle-swagger-ui-2.png %}){:width="740px"}

![Swagger UI]({% image_path inventory-spring-boot-gradle-swagger-ui-3.png %}){:width="740px"}

Now let's check if path `/inventory/{itemId}` works, please click on that path and then on `Try it out`.

![Swagger UI]({% image_path inventory-spring-boot-gradle-swagger-ui-4.png %}){:width="740px"}

Now enter any itemId (as you know the generated code will send an example response) and click on `Execute`.

![Swagger UI]({% image_path inventory-spring-boot-gradle-swagger-ui-5.png %}){:width="740px"}

You should get an answer as in the next picture, a sample InventoryItem object (red rectangle). 

> Pay attention to the (expected) error returned, 501 and also to the actual path /api/inventory/22222.
>
> * Base-path is `/api` because we defined a Server when designing the API
> * `Error 501` was already mentioned and is there just to remind you that this response is an example and should be implemented eventually.

![Swagger UI]({% image_path inventory-spring-boot-gradle-swagger-ui-6.png %}){:width="740px"}

So far so good, now we have to create a git repo and push our code to it.

#### Creating a git repo for the generated code

You can use any Git server (e.g. GitHub, BitBucket, etc) for this lab but we have prepared a Gogs git server which you can access here: 

{{ GIT_URL }}

Click on **Register** to register a new user with the following details and then click on 
**Create New Account**: 

* Username: _same as your OpenShift user_
* Email: *your email*  (Don't worry! Gogs won't send you any emails)
* Password: `openshift`

> **Email** could be <username>@ocp.com

![Sign Up Gogs]({% image_path cd-gogs-signup.png %}){:width="900px"}

You will be redirected to the sign in page. Sign in using the above username and password.

Click on the plus icon on the top navigation bar and then on **New Repository**.

![Create New Repository]({% image_path inventory-spring-boot-gradle-git-new-repo-01.png %}){:width="900px"}

Give `inventory-spring-boot-gradle` as **Repository Name** and click on **Create Repository** 
button, leaving the rest with default values.

![Create New Repository]({% image_path inventory-spring-boot-gradle-git-new-repo-02.png %}){:width="700px"}

The Git repository is created now. 

Click on the copy-to-clipboard icon to near the *HTTP Git url* to copy it to the clipboard which you will need in a few minutes.

![Empty Repository]({% image_path inventory-spring-boot-gradle-git-new-repo-03.png %}){:width="900px"}

#### Push Inventory Code to the Git Repository

Now that you have a Git repository for the Inventory service, you should push the source code into this Git repository.

> **NOTE:** If you skipped the previous chapter you should skip this one too...

Go the folder where we have generated the code (it should be `inventory-spring-boot-gradle/inventory`) folder, initialize it as a Git working copy and add the GitHub repository as the remote repository for your working copy. 

> Replace `GIT-REPO-URL` with the Git repository url copied in the previous steps

> Make sure you're at `inventory-spring-boot-gradle/inventory` and that OUTPUT_DIR is defined and properly populated. If in doubt review chapter **Generating the code**!

~~~shell
$ git init
$ git remote add origin GIT-REPO-URL
~~~

Before you commit the source code to the Git repository, configure your name and email so that the commit owner can be seen on the repository. If you want, you can replace the name and the email with your own in the following commands:

~~~shell
$ git config user.name "userXX"
$ git config user.email "userXX@ocp.com"
~~~

> **<font size="3" color="red">Attention:</font>** we need to delete the pom.xml file so that later the builder image don't default to Maven instead of using Gradle. More on this later... for now please delete the file.

~~~shell
$ rm pom.xml
~~~

Commit and push the existing code to the GitHub repository.

~~~shell
$ git add . --all
$ git commit -m "initial add"
$ git push -u origin master
~~~

Enter your Git repository username and password if you get asked to enter your credentials. Go to your `inventory-spring-boot-gradle` repository web interface and refresh the page. You should see the project files in the repository.

![Inventory Repository]({% image_path inventory-spring-boot-gradle-git-new-repo-04.png %}){:width="900px"}

#### Deploying our Spring Boot API on OpenShift

Deploying a Gradle application on Openshift is no different than deploying a Java or NodeJS application using Openshift S2I (or Source to Image).

> [Source-to-Image (S2I)](https://docs.openshift.com/container-platform/3.11/architecture/core_concepts/builds_and_image_streams.html#source-build) is a framework that makes it easy to write images that take application source code as an input and produce a new image that runs the assembled application as output.
> The main advantage of using S2I for building reproducible container images is the ease of use for developers. As a builder image author, you must understand two basic concepts in order for your images to provide the best possible S2I performance: the build process and S2I scripts.

Basically deploying code using S2I means using an builder image that understands our code to generate an image with the runtime version of our code 'artifact' (JAR, WAR, compiled code, ...) ready to be run; it also means generating some descriptors (ImageStream, DeploymentConfig, Service).

But before we deploy our code we must first create a project in Openshift, so please open your favorite browser and open the Openshift web console. Click on `Create Project` as in the following picture.

> **NOTE:** If you have already created a project named {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} and another one {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev) you don't have to (and by the way you can't).

~~~shell
$ oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
$ oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
~~~

> Either if you have just created the required projects or not, **please make sure project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` is in use** by running: `oc project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`

Ok, so we're sure we have a project to deploy our API, well, let's deploy our code. To do so, as mentioned before, we need a builder image capable of using Gradle, `fabric8/s2i-java` is such an image. So all that is left is using command `oc new-app` and let S2I do the rest.

> **<font size="3" color="red">Remember:</font>** to substitute GIT-REPO-URL by the actual URL of your git repository

~~~shell
$ oc new-app fabric8/s2i-java~GIT-REPO-URL --context-dir=. --name inventory-s2i
--> Found Docker image be28740 (30 hours old) from Docker Hub for "fabric8/s2i-java"

    Java Applications 
    ----------------- 
    Platform for building and running plain Java applications (fat-jar and flat classpath)

    Tags: builder, java

    * An image stream tag will be created as "s2i-java:latest" that will track the source image
    * A source build using source code from GIT-REPO-URL will be created
      * The resulting image will be pushed to image stream tag "inventory-s2i:latest"
      * Every time "s2i-java:latest" changes a new build will be triggered
    * This image will be deployed in deployment config "inventory-s2i"
    * Ports 8080/tcp, 8778/tcp, 9779/tcp will be load balanced by service "inventory-s2i"
      * Other containers can access this service through the hostname "inventory-s2i"

--> Creating resources ...
    imagestream.image.openshift.io "inventory-s2i" created
    buildconfig.build.openshift.io "inventory-s2i" created
    deploymentconfig.apps.openshift.io "inventory-s2i" created
    service "inventory-s2i" created
--> Success
    Build scheduled, use 'oc logs -f bc/inventory-s2i' to track its progress.
    Application is not exposed. You can expose services to the outside world by executing one or more of the commands below:
     'oc expose svc/inventory-s2i' 
    Run 'oc status' to view your app.
~~~

If you go the Openshift web console and head to the project where you have created your first Gradle application you should see how the Build task progesses and evetually how the pod is started and ready to receive requests.

> *Once the pod is ready, it's color changes to bright blue, before it should change from gray to light blue.*

![Deploying on OCP]({% image_path inventory-spring-boot-gradle-deploy-api-01.png %}){:width="740px"}

> *At some point you should see that Gradle is being use, as in the next picture.*

![Deploying on OCP]({% image_path inventory-spring-boot-gradle-deploy-api-02.png %}){:width="500px"}

After a successful built you should see a blue circle stating that 1 pod is up and running. 

> So far our code is alive and kicking but you cannot access it from the internet... To solve this you can either click on `Create Route` or run a very simple command `oc expose` as explained in the next step.

![Deploying on OCP]({% image_path inventory-spring-boot-gradle-deploy-api-03.png %}){:width="740px"}

In order to expose our service to the internet we need a route, next command will do the job.

~~~shell
$ oc expose svc/inventory-s2i
route.route.openshift.io/inventory-s2i exposed
~~~

Now if you go back to the Openshift web console you should see the route generated.

![Deploying on OCP]({% image_path inventory-spring-boot-gradle-deploy-api-04.png %}){:width="740px"}

Finally, let's check that our API works properly in Openshift. Please click on the route URL, and run some tests as we did before.

![Deploying on OCP]({% image_path inventory-spring-boot-gradle-deploy-api-05.png %}){:width="740px"}

Do you see an annoying red badge near the bottom right corner? Yes? It is harmless but If you want to get rid of it all you have to do is adding the following code (`function uiConfig()`) to class `OpenAPIDocumentationConfig` in package `org.openapitools.configuration`.

~~~java
...
import springfox.documentation.swagger.web.UiConfiguration;
import springfox.documentation.swagger.web.UiConfigurationBuilder;
...
public class OpenAPIDocumentationConfig {
	...
	@Bean
    UiConfiguration uiConfig() {
        return UiConfigurationBuilder.builder()
            .displayRequestDuration(true)
            .validatorUrl("")
            .build();
    }
    
 class BasePathAwareRelativePathProvider extends RelativePathProvider {
 ...
~~~

Once you have modified your code you need to commit, push and start a new build.

~~~shell
$ git commit -a -m "validatorUrl empty"
$ git push -u origin master
$ oc start-build bc/inventory-s2i
~~~

> **TIP:** You can use this command to see the progress of the build `oc logs -f bc/inventory-s2i`

Well done! You are ready to move on to the next lab.
