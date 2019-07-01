## Alternative Inventory Service using Spring Boot + Maven

In this lab you will learn about **how you can build microservices using** **Spring Boot + Maven** and **Red Hat Openshift**. During this lab, you will use [**Open API Generator**](https://github.com/openapitools/openapi-generator), a code generator that can generate API client libraries, server stubs, documentation and configuration automatically given an [**Open API**](https://www.openapis.org/) Spec.

You will use a previously created Open API specification or you'll be given one by the instructor.

#### Preprequisites

In order to follow this lab you'll need:

* **Java 8**, for the Open API Generator. You can find it [here](https://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html)

* **Apache Maven 3.3.x or later**, for packaging, deploying, etc. You can find it [here](https://archive.apache.org/dist/maven/maven-3).

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

We are going to use the OpenAPI Generator CLI to generate a Maven project.

Now, please create a folder where will generate the code.

~~~shell
$ mkdir inventory-spring-boot-maven
$ cd inventory-spring-boot-maven
~~~

> Copy `Inventory API.yaml` (or the name you gave it when you download it) to folder `inventory-spring-boot-maven`.

~~~shell
$ cp ~/Downloads/Inventory\ API.yaml ./inventory.yaml
~~~

Next you'll create a `bin` folder, download the API Generator CLI...

~~~shell
$ cat << EOF > openapi-config.json
{
    "basePackage" : "com.redhat.cloudnative.inventory",
    "configPackage" : "com.redhat.cloudnative.inventory.config",
    "invokerPackage" : "com.redhat.cloudnative.inventory.invoker",
    "apiPackage" : "com.redhat.cloudnative.inventory.api",
    "modelPackage" : "com.redhat.cloudnative.inventory.model",
    "artifactId" : "inventory",
    "artifactVersion" : "0.0.1-SNAPSHOT",
    "serializableModel" : true
}
EOF
$ mkdir ./bin
$ curl -L -o ./bin/openapi-generator-cli.jar http://central.maven.org/maven2/org/openapitools/openapi-generator-cli/3.3.4/openapi-generator-cli-3.3.4.jar
$ export OUTPUT_DIR="inventory-gen"
~~~

The whole purpose of this chapter is to generate code from the API specification we just designed. **But before we do we have to handle a mismatch between the specification generated by Apicurio and the specification expected by the OpenAPI Generator plugin**.

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

Finally let's generate the code.

~~~shell
$ java -jar ./bin/openapi-generator-cli.jar generate \
  -i inventory.yaml \
  -l spring \
  -o $OUTPUT_DIR -c openapi-config.json
~~~

So, where did the generated code go? Well, because we have defined our own `openapi-config.json` file, code should be Spring, and located in packages as follows (main class/interface highlighted):

* `com.redhat.cloudnative.inventory` → **OpenAPI2SpringBoot**
* `com.redhat.cloudnative.inventory.api` → **InventoryApi**
* `com.redhat.cloudnative.inventory.model` → **GenericError** and **InventoryItem**
* `com.redhat.cloudnative.inventory.config` → **HomeController** and **OpenAPIDocumentationConfig**


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

Now we can run our brand new API implementation, and see if it works.

~~~shell
$ mvn spring-boot:run
[INFO] Scanning for projects...
...
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v2.0.1.RELEASE)
...
2019-04-23 11:14:18.659  INFO 59249 --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : FrameworkServlet 'dispatcherServlet': initialization completed in 15 ms

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

> **Email** could be `<username>@ocp.com`

![Sign Up Gogs]({% image_path gogs-signup.png %}){:width="900px"}

You will be redirected to the sign in page. Sign in using the above username and password.

Click on the plus icon on the top navigation bar and then on **New Repository**.

![Create New Repository]({% image_path gogs-new-repo-01.png %}){:width="900px"}

Give `inventory-spring-boot-gradle` as **Repository Name** and click on **Create Repository** 
button, leaving the rest with default values.

![Create New Repository]({% image_path gogs-new-repo-02.png %}){:width="700px"}

The Git repository is created now. 

Click on the copy-to-clipboard icon to near the *HTTP Git url* to copy it to the clipboard which you will need in a few minutes.

![Empty Repository]({% image_path gogs-new-repo-03.png %}){:width="900px"}

#### Push Inventory Code to the Git Repository

Now that you have a Git repository for the Inventory service, you should push the source code into this Git repository.

> **NOTE:** If you skipped the previous chapter you should skip this one too...

Go to the folder where we have generated the code (it should be `inventory-spring-boot-maven/inventory-gen`) folder, initialize it as a Git working copy and add the GitHub repository as the remote repository for your working copy. 

> Replace `GIT-REPO-URL` with the Git repository url copied in the previous steps

> Make sure you're at `inventory-spring-boot-maven/inventory-gen` and that OUTPUT_DIR is defined and properly populated. If in doubt review chapter **Generating the code**!

~~~shell
$ git init
$ git remote add origin GIT-REPO-URL
~~~

Before you commit the source code to the Git repository, configure your name and email so that the commit owner can be seen on the repository. If you want, you can replace the name and the email with your own in the following commands:

~~~shell
$ git config user.name "userXX"
$ git config user.email "userXX@ocp.com"
~~~

Commit and push the existing code to the GitHub repository.

~~~shell
$ git add . --all
$ git commit -m "initial add"
$ git push -u origin master
~~~

Enter your Git repository username and password if you get asked to enter your credentials. Go to your `inventory-g2` repository web interface and refresh the page. You should see the project files in the repository.

![Inventory Repository]({% image_path gogs-new-repo-04.png %}){:width="900px"}

#### Deploying our Spring Boot API on OpenShift

The easiest way to deploy a Maven Spring Boot application on Openshift is done using Openshift S2I (or Source to Image).

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

Ok, so we're sure we have a project to deploy our API, well, let's deploy our code. To do so, as mentioned before, we need a builder image capable of using Maven,  `openshift/java` is such an image,  `fabric8/s2i-java` would work as well. So all that is left is using command `oc new-app` and let S2I do the rest.

> **<font size="3" color="red">Remember:</font>** to substitute GIT-REPO-URL by the actual URL of your git repository
> The structure of the following command is as follows: `oc new-app` 

~~~shell
$ oc new-app java:8~GIT-REPO-URL --context-dir=. --name inventory-s2i
--> Found image 8bea1b6 (2 weeks old) in image stream "openshift/java" under tag "8" for "java:8"

    Java Applications 
    ----------------- 
    Platform for building and running plain Java applications (fat-jar and flat classpath)

    Tags: builder, java

    * A source build using source code from http://gogs-lab-infra.apps.serverless-c8c1.openshiftworkshop.com/developer/inventory-g2.git will be created
      * The resulting image will be pushed to image stream tag "inventory-s2i:latest"
      * Use 'start-build' to trigger a new build
    * This image will be deployed in deployment config "inventory-s2i"
    * Ports 8080/tcp, 8443/tcp, 8778/tcp will be load balanced by service "inventory-s2i"
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

So far, so good, the process of building the image from the source code is running. If you go the Openshift web console and head to the project where you have created your first Spring Boot Maven application, you should see how the Build task progesses and evetually how the pod is started and ready to receive requests.

> *Once the pod is ready, it's color changes to bright blue, before it should change from gray to light blue.*

![Deploying on OCP]({% image_path inventory-spring-boot-maven-deploy-api-01.png %}){:width="740px"}

> *At some point you should see that Maven is being use, as in the next picture.*

![Deploying on OCP]({% image_path inventory-spring-boot-maven-deploy-api-02.png %}){:width="500px"}

After a successful built you should see a blue circle stating that 1 pod is up and running. 

> So far our code is alive and kicking but you cannot access it from the internet... To solve this you can either click on `Create Route` or run a very simple command `oc expose` as explained in the next step.

![Deploying on OCP]({% image_path inventory-spring-boot-maven-deploy-api-03.png %}){:width="740px"}

In order to expose our service to the internet we need a route, next command will do the job.

~~~shell
$ oc expose svc/inventory-s2i
route.route.openshift.io/inventory-s2i exposed
~~~

Now if you go back to the Openshift web console you should see the route generated.

![Deploying on OCP]({% image_path inventory-spring-boot-maven-deploy-api-04.png %}){:width="740px"}

Finally, let's check that our API works properly in Openshift. Please click on the route URL, and run some tests as we did before.

![Deploying on OCP]({% image_path inventory-spring-boot-maven-deploy-api-05.png %}){:width="740px"}

Do you see an annoying red badge near the bottom right corner? Yes? It is harmless but If you want to get rid of it all you have to do is adding the following code (`function uiConfig()`) to class `OpenAPIDocumentationConfig` in package `com.redhat.cloudnative.inventory.config`.

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