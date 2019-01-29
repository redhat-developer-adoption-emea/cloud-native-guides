## Microservices with .NET Core 2.x

In this lab you will learn about **how you can build microservices using** **.Net Core** and **Red Hat Openshift**. During this lab, you will use [**Open API Generator**](https://github.com/openapitools/openapi-generator), a code generator that can generate API client libraries, server stubs, documentation and configuration automatically given an [**Open API**](https://www.openapis.org/) Spec.

You will use a previously created Open API specification or you'll be given one by the instructor.

#### What is .NET Core?

From Wikipedia:

> * **ASP.NET Core** is a free and **open-source web framework**, and higher performance than ASP.NET, **developed by Microsoft and the community**. It is a modular framework that runs on both the full .NET Framework, on Windows, and the cross-platform .NET Core. 
> * The framework **is a complete rewrite that unites the previously separate ASP.NET MVC and ASP.NET Web API into a single programming model**.

Red Hat Openshift supports a [list of images](https://access.redhat.com/articles/2176281) intended to be used as base layer images, and provide functionality to developers on the OpenShift platform. Among them: .NET Core 1.0, .NET Core 1.1, .NET Core 2.1 

You can find more information about .Net Core in RHEL [here](https://developers.redhat.com/products/dotnet/overview/)

#### Preprequisites

In order to follow this lab you'll need:

* **Java 8**, for the Open API Generator. You can find it [here](https://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html)
* **.Net Core Build apps** - SDK, to test locally the code generated. If you don't want/care to run tests locally you don't need this. You can find it from [here](https://dotnet.microsoft.com/download/dotnet-core/2.1)

#### Retrieving the API specification

If you have executed the API desing lab go to [API Curio](https://www.apicur.io/), log in as you did before and navigate to the Inventory API.

> If you haven't executed the previous lab the instructor will hand you the YAML file with the API specification

![apicur.io landing page]({% image_path dotnet-apicurio-retrieve-yaml-01.png %}){:width="740px"}

Click on `View All APIs`

![apicur.io landing page]({% image_path dotnet-apicurio-retrieve-yaml-02.png %}){:width="740px"}

Click on `Inventory API`.

![apicur.io landing page]({% image_path dotnet-apicurio-retrieve-yaml-03.png %}){:width="300px"}

Click on the menu as in the picture, then click on `Download (YAML)`

![apicur.io landing page]({% image_path dotnet-apicurio-retrieve-yaml-04.png %}){:width="400px"}

#### Generating the code

In ordet to generate the C# (.Net Core) code (stub/façade) we're going to use the [**Open API Generator**](https://github.com/openapitools/openapi-generator). These tool parses the YAML file you've hopefully retrieve and generates an REST API following the speficication, including the example provided, if any, for the different response actions defined.

> **Note:** we're going to use a configuration file (`openapi-config.json`) in order to change the generated project/package name from a default name to CoolstoreXX.Inventory

With the next set of commands we're going to create a folder, change to it, create a config file (mentioned above), download Open API Generator CLI and use it to generate the .Net code from our API specification.

**For Windows systems.**

Let's create a folder named `inventory-dotnet-core-lab` for this lab.

~~~shell
> mkdir inventory-dotnet-core-lab
~~~

> Copy `Inventory API.yaml` (or the name you gave it when you download it) to folder `inventory-dotnet-core-lab`.

Next you'll create a `bin` folder, download the API Generator CLI...

~~~shell
> cd inventory-dotnet-core-lab
> mkdir .\bin
> bitsadmin /create myDownloadJob
> bitsadmin /addfile myDownloadJob http://central.maven.org/maven2/org/openapitools/openapi-generator-cli/3.3.4/openapi-generator-cli-3.3.4.jar .\bin\openapi-generator-cli.jar
> bitsadmin /info myDownloadJob /verbose
~~~

Wait until the download job has finished... then run.

~~~shell
> bitsadmin /complete myDownloadJob
> set OUTPUT_DIR="inventory-gen"
~~~

Open an editor and copy/paste this content, then save it in `inventory-dotnet-core-lab`.

~~~json
{
    "packageName" : "CoolstoreXX.Inventory"
}
~~~

Finally let's generate the code.

~~~shell
> java -jar ./bin/openapi-generator-cli.jar generate -i "Inventory API.yaml" -g aspnetcore -o %OUTPUT_DIR% -c openapi-config.json
~~~

**For *nix systems.**

Let's create a folder named `inventory-dotnet-core-lab` for this lab.

~~~shell
$ mkdir inventory-dotnet-core-lab
~~~

> Copy `Inventory API.yaml` (or the name you gave it when you download it) to folder `inventory-dotnet-core-lab`.

Next you'll create a `bin` folder, download the API Generator CLI...

~~~shell
$ cd inventory-dotnet-core-lab
$ cat << EOF > openapi-config.json
{
    "packageName" : "CoolstoreXX.Inventory"
}
EOF
$ mkdir ./bin
$ curl -L -o ./bin/openapi-generator-cli.jar http://central.maven.org/maven2/org/openapitools/openapi-generator-cli/3.3.4/openapi-generator-cli-3.3.4.jar
$ export OUTPUT_DIR="inventory-gen"
~~~

Finally let's generate the code.

~~~shell
$ java -jar ./bin/openapi-generator-cli.jar generate -i Inventory\ API.yaml -g aspnetcore -o $OUTPUT_DIR -c openapi-config.json
~~~

#### Restoring NuGet packages

> Please change to folder OUTPUT_DIR, if you haven't already.

Now let's restore NuGet packages before proceeding to fix the errors.

~~~shell
dotnet restore
~~~

You should see something like this...

~~~shell
Restoring packages for .../src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj...
  Restore completed in 82.5 ms for .../src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj.
  Generating MSBuild file .../src/CoolstoreXX.Inventory/obj/CoolstoreXX.Inventory.csproj.nuget.g.props.
  Generating MSBuild file .../src/CoolstoreXX.Inventory/obj/CoolstoreXX.Inventory.csproj.nuget.g.targets.
  Restore completed in 1.5 sec for .../src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj.
~~~

#### [Temporary] Fixing the error in DefaultApi.cs

[Open API Generator](https://github.com/openapitools/openapi-generator) generates a proper C# API server stub but introduces a couple of errors when copying the reponse examples provided within the specification.

These two errors are in `src/CoolstoreXX.Inventory/Controllers/DefaultApi.cs` please follow the instructions below to fix them.

> Please change to folder OUTPUT_DIR, if you haven't already, and open Visual Studio Code there.

Locate the following piece of code in `DefaultApi.cs`, as you can see corresponds to the `GET` operation to get all the inventory items.

> If you see more errors please check you have run the `restore` command to restore the needed NuGet packages

**Original code**

~~~csharp
[HttpGet]
[Route("/api/inventory")]
[ValidateModelState]
[SwaggerOperation("ApiInventoryGet")]
[SwaggerResponse(statusCode: 200, type: typeof(List<InventoryItem>), description: "Should return an arry of InventoryItems")]
public virtual IActionResult ApiInventoryGet()
{ 
    //TODO: Uncomment the next line to return response 200 or use other options such as return this.NotFound(), return this.BadRequest(..), ...
    // return StatusCode(200, default(List<InventoryItem>));

    string exampleJson = null;
    exampleJson = "\"{\\"itemId\\":\\"329299\\",\\"quantity\\":35}\"";
    
    var example = exampleJson != null
    ? JsonConvert.DeserializeObject<List<InventoryItem>>(exampleJson)
    : default(List<InventoryItem>);
    //TODO: Change the data returned
    return new ObjectResult(example);
}
~~~

The offending line is:

~~~csharp
exampleJson = "\"{\\"itemId\\":\\"329299\\",\\"quantity\\":35}\"";
~~~

It should be:

~~~csharp
exampleJson = "[{\"itemId\":\"329299\",\"quantity\":35},{\"itemId\":\"329199\",\"quantity\":12},{\"itemId\":\"165613\",\"quantity\":45},{\"itemId\":\"165614\",\"quantity\":87},{\"itemId\":\"165954\",\"quantity\":43},{\"itemId\":\"444434\",\"quantity\":32},{\"itemId\":\"444435\",\"quantity\":53}]";
~~~

Now let's fix the second error. Please locate the following piece of code in `DefaultApi.cs`, this time the code corresponds to the `GET` operation to get the inventory item given an id. 

~~~csharp
/// <summary>
/// 
/// </summary>
/// <remarks>Returns the item for the id provided or an error</remarks>
/// <param name="itemId"></param>
/// <response code="200">Should return the item for the id provided</response>
/// <response code="404">Item not found</response>
[HttpGet]
[Route("/api/inventory/{itemId}")]
[ValidateModelState]
[SwaggerOperation("ApiInventoryItemIdGet")]
[SwaggerResponse(statusCode: 200, type: typeof(InventoryItem), description: "Should return the item for the id provided")]
[SwaggerResponse(statusCode: 404, type: typeof(GenericError), description: "Item not found")]
public virtual IActionResult ApiInventoryItemIdGet([FromRoute][Required]string itemId)
{ 
    //TODO: Uncomment the next line to return response 200 or use other options such as return this.NotFound(), return this.BadRequest(..), ...
    // return StatusCode(200, default(InventoryItem));

    //TODO: Uncomment the next line to return response 404 or use other options such as return this.NotFound(), return this.BadRequest(..), ...
    // return StatusCode(404, default(GenericError));

    string exampleJson = null;
    exampleJson = "\"{\\"itemId\\":\\"329299\\",\\"quantity\\":35}\"";
    
    var example = exampleJson != null
    ? JsonConvert.DeserializeObject<InventoryItem>(exampleJson)
    : default(InventoryItem);
    //TODO: Change the data returned
    return new ObjectResult(example);
}
~~~

The offending line is:

~~~csharp
exampleJson = "\"{\\"itemId\\":\\"329299\\",\\"quantity\\":35}\"";
~~~

It should be:

~~~csharp
exampleJson = "{\"itemId\":\"329299\",\"quantity\":35}";
~~~

> If you're curious the original JSON version of our specification is here `src/CoolstoreXX.Inventory/wwwroot/openapi-original.json`

#### Testing the API

In order to test the API locally, you need to be in the folder where we have run the commands to generate the code, **OUTPUT_DIR**. From that folder you should be able to run the following command.

> Pay attention to the `-p` flag, as you can see it points to the project file inside **OUTPUT_DIR**. This environment variable was populated before with `inventory-gen`

~~~shell
dotnet run -p ./src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj
~~~

You should see something like...

~~~shell
Hosting environment: Development
Content root path: .../inventory-dotnet-core-lab/inventory-gen/src/CoolstoreXX.Inventory
Now listening on: http://0.0.0.0:8080
Application started. Press Ctrl+C to shut down.
~~~

Open a browser and open http://localhost:8080.

![Pipeline Log]({% image_path dotnet-swagger-ui-tests.png %}){:width="740px"}


Let's test both the `/api/inventory` and `/api/inventory/{itemId}`, for the former you should get.

~~~json
[{"itemId":"329299","quantity":35},{"itemId":"329199","quantity":12},{"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},{"itemId":"165954","quantity":43},{"itemId":"444434","quantity":32},{"itemId":"444435","quantity":53}]
~~~

For the latter you should get this.

~~~json
{"itemId":"329299","quantity":35}
~~~

#### Adding monitoring support to your API with Prometheus

> **Prometheus** is an open-source systems monitoring and alerting toolkit originally built at [SoundCloud](http://soundcloud.com/). Since its inception in 2012, many companies and organizations have adopted Prometheus, and the project has a very active developer and user community. It is now a standalone open source project and maintained independently of any company. To emphasize this, and to clarify the project's governance structure, Prometheus joined the [Cloud Native Computing Foundation](https://cncf.io/) in 2016 as the second hosted project, after Kubernetes.

For this lab, Prometheus has already been provisioned, ask your instructor about how you can do it yourself with [Operator Framework](https://docs.openshift.com/container-platform/3.11/install_config/installing-operator-framework.html).

Prometheus will expect your API to expose metrics in a certain way in a given endpoint `/metrics`.

> Check you're in the **OUTPUT_DIR** before proceeding.

Let's add a couple of packages to our API via NuGet to provide to our API with the Prometheus metrics endpoint.

> These packages are open source and the code can be found [here](https://github.com/prometheus-net/prometheus-net).

~~~shell
dotnet add src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj package prometheus-net
dotnet add src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj package prometheus-net.AspNetCore
~~~

Now we have modify our `Startup.cs` to inject Prometheus support. Please open` ./src/CoolstoreXX.Inventory/Startup.cs` and locate function `Configure()`. Add the following right after **app.UseHttpsRedirection();**

~~~shell
// Prometheus support
app.UseMetricServer();
~~~

...as in here
	
~~~csharp
public void Configure(IApplicationBuilder app, IHostingEnvironment env)
{
    app.UseHttpsRedirection();
    
    // Prometheus support
    app.UseMetricServer();

    app
        .UseMvc()
    ...
~~~

Don't forget import the Prometheus library `using Prometheus;`

So far, we have added Prometheus support and if you execute the code again you should be able to invoke `/metrics` but we haven't added our own "business" metrics, so let's do it now.

Go to **./src/Controllers/DefaultApi.cs** and locate **public class DefaultApiController : ControllerBase**, we're going to add a private property of type Counter and a constructor to declare it.

Please modify your **DefaultApiController** as follows... As you can see we're defining three labes, namely:

- **api**, for the API name, inventory in our case...
- **method**, POST, GET, etc.
- **endpoint**, /api/...

~~~csharp
public class DefaultApiController : ControllerBase
{ 
    private Counter apiHttpRequestsTotalCounter;

    public DefaultApiController()
    {
        apiHttpRequestsTotalCounter = Metrics.CreateCounter("api_http_requests_total", "Counts get ...", new CounterConfiguration {
            LabelNames = new[] { "api", "method", "endpoint" }
        });
    }
    
    /// <summary>
    /// 
    ...
~~~

Now that we have declared and defined our counter it's time to use it. We want to count everytime a request is received in `/api/inventory` and in `/api/inventory/{itemId}`.

Locate the function **ApiInventoryGet** and add...

~~~csharp
apiHttpRequestsTotalCounter.WithLabels("inventory", "GET", "/api/inventory").Inc();
~~~

As in here...

~~~csharp
[HttpGet]
[Route("/api/inventory")]
[ValidateModelState]
[SwaggerOperation("ApiInventoryGet")]
[SwaggerResponse(statusCode: 200, type: typeof(List<InventoryItem>), description: "Should return an arry of InventoryItems")]
public virtual IActionResult ApiInventoryGet()
{ 
    apiHttpRequestsTotalCounter.WithLabels("inventory", "GET", "/api/inventory").Inc();

    //TODO: Uncomment the next line to return response 200 or use other options such as return this.NotFound(), return this.BadRequest(..), ...
    // return StatusCode(200, default(List<InventoryItem>));

    string exampleJson = null;
    exampleJson = "[{\"itemId\":\"329299\",\"quantity\":35},{\"itemId\":\"329199\",\"quantity\":12},{\"itemId\":\"165613\",\"quantity\":45},{\"itemId\":\"165614\",\"quantity\":87},{\"itemId\":\"165954\",\"quantity\":43},{\"itemId\":\"444434\",\"quantity\":32},{\"itemId\":\"444435\",\"quantity\":53}]";
    
    var example = exampleJson != null
    ? JsonConvert.DeserializeObject<List<InventoryItem>>(exampleJson)
    : default(List<InventoryItem>);
    //TODO: Change the data returned
    return new ObjectResult(example);
}
~~~

Now locate the function **ApiInventoryItemIdGet** and add...

~~~csharp
apiHttpRequestsTotalCounter.WithLabels("inventory", "GET", "/api/inventory/{itemId}").Inc();
~~~

As in here...

~~~csharp
[HttpGet]
[Route("/api/inventory/{itemId}")]
[ValidateModelState]
[SwaggerOperation("ApiInventoryItemIdGet")]
[SwaggerResponse(statusCode: 200, type: typeof(InventoryItem), description: "Should return the item for the id provided")]
[SwaggerResponse(statusCode: 404, type: typeof(GenericError), description: "Item not found")]
public virtual IActionResult ApiInventoryItemIdGet([FromRoute][Required]string itemId)
{ 
    apiHttpRequestsTotalCounter.WithLabels("inventory", "GET", "/api/inventory/{itemId}").Inc();
    
    //TODO: Uncomment the next line to return response 200 or use other options such as return this.NotFound(), return this.BadRequest(..), ...
    // return StatusCode(200, default(InventoryItem));

    //TODO: Uncomment the next line to return response 404 or use other options such as return this.NotFound(), return this.BadRequest(..), ...
    // return StatusCode(404, default(GenericError));

    string exampleJson = null;
    exampleJson = "{\"itemId\":\"329299\",\"quantity\":35}";
    
    var example = exampleJson != null
    ? JsonConvert.DeserializeObject<InventoryItem>(exampleJson)
    : default(InventoryItem);
    //TODO: Change the data returned
    return new ObjectResult(example);
}
~~~

Finally, let's run our code again.

~~~shell
dotnet run -p ./src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj
~~~

Open a browser and test the APIs as we did before.

Once you have tested some times both API endpoints please open another tab in your browser and point to `/metrics`, you should get something like this. In this case **4** request were processed in **/api/inventory/{itemId}** and only **2** in **/api/inventory**

> You can check that apart from our counter... there other useful metrics...

~~~shell
# HELP dotnet_totalmemory Total known allocated memory
# TYPE dotnet_totalmemory gauge
dotnet_totalmemory 8026240
# HELP dotnet_collection_errors_total Total number of errors that occured during collections
# TYPE dotnet_collection_errors_total counter
dotnet_collection_errors_total 0
...
# HELP api_http_requests_total Counts get ...
# TYPE api_http_requests_total counter
api_http_requests_total{api="inventory",method="GET",endpoint="/api/inventory"} 2
api_http_requests_total{api="inventory",method="GET",endpoint="/api/inventory/{itemId}"} 4
...
~~~

So far so good, now we have to create a git repo and push our code to it.

#### Creating a git repo for the generated code

You can use any Git server (e.g. GitHub, BitBucket, etc) for this lab but we have prepared a Gogs git server which you can access here: 

{{ GIT_URL }}

> **TIP:** Or you could just use a repo prepared with a ready to use copy of the code you've previously generated. This repo is [here]({{ GIT_URL }}/user99/inventory-dotnet-core.git)

Click on **Register** to register a new user with the following details and then click on 
**Create New Account**: 

* Username: _same as your OpenShift user_
* Email: *your email*  (Don't worry! Gogs won't send you any emails)
* Password: `openshift`

![Sign Up Gogs]({% image_path cd-gogs-signup.png %}){:width="900px"}

You will be redirected to the sign in page. Sign in using the above username and password.

Click on the plus icon on the top navigation bar and then on **New Repository**.

![Create New Repository]({% image_path dotnet-git-new-repo-01.png %}){:width="900px"}

Give `inventory-dotnet-core` as **Repository Name** and click on **Create Repository** 
button, leaving the rest with default values.

![Create New Repository]({% image_path dotnet-git-new-repo-02.png %}){:width="700px"}

The Git repository is created now. 

Click on the copy-to-clipboard icon to near the *HTTP Git url* to copy it to the clipboard which you will need in a few minutes.

![Empty Repository]({% image_path dotnet-git-new-repo-03.png %}){:width="900px"}

#### Push Inventory Code to the Git Repository

Now that you have a Git repository for the Inventory service, you should push the source code into this Git repository.

> **NOTE:** If you skipped the previous chapter you should skip this one too...

Go the `inventory-dotnet-core` folder, initialize it as a Git working copy and add the GitHub repository as the remote repository for your working copy. 

> Replace `GIT-REPO-URL` with the Git repository url copied in the previous steps

> Make sure you're at `inventory-dotnet-core-lab` and that OUTPUT_DIR is defined and properly populated. If in doubt review chapter **Generating the code**!

~~~shell
$ cd $OUTPUT_DIR
$ git init
$ git remote add origin GIT-REPO-URL
~~~

Before you commit the source code to the Git repository, configure your name and email so that the commit owner can be seen on the repository. If you want, you can replace the name and the email with your own in the following commands:

~~~shell
git config --global user.name "userXX"
git config --global user.email "userXX@ocp.com"
~~~

Commit and push the existing code to the GitHub repository.

~~~shell
$ git add . --all
$ git commit -m "initial add"
$ git push -u origin master
~~~

Enter your Git repository username and password if you get asked to enter your credentials. Go to your `inventory-dotnet-core` repository web interface and refresh the page. You should see the project files in the repository.

![Inventory Repository]({% image_path dotnet-git-new-repo-04.png %}){:width="900px"}

#### Deploying our .Net Core API on OpenShift

Deploying a .Net Core application on Openshift is no different than deploying a Java or NodeJS application using Openshift S2I (or Source to Image).

> [Source-to-Image (S2I)](https://docs.openshift.com/container-platform/3.11/architecture/core_concepts/builds_and_image_streams.html#source-build) is a framework that makes it easy to write images that take application source code as an input and produce a new image that runs the assembled application as output.
> The main advantage of using S2I for building reproducible container images is the ease of use for developers. As a builder image author, you must understand two basic concepts in order for your images to provide the best possible S2I performance: the build process and S2I scripts.

But before we deploy our code we must first create a project in Openshift, so please open your favorite browser and open the Openshift web console. Click on `Create Project` as in the following picture.

> **NOTE:** In order to shorten the time needed for this lab, this step may have been taken care for you (please check if you alredy have a project named {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} and another one {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev). However if you want to create a different project be sure to tell your instructor because the lab and Prometheus itself is expecting a specific project name.

![Inventory Repository]({% image_path dotnet-create-project.png %}){:width="900px"}

In order to deploy our API using the web console, open project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` and click on `Catalog` (bottom-left corner). Now choose `.Net Core`.

![Deploying on OCP]({% image_path dotnet-deploy-api-01.png %}){:width="740px"}

Click on `Next`.

![Deploying on OCP]({% image_path dotnet-deploy-api-02.png %}){:width="600px"}

Name your application as `inventory-dotnet-core` and copy the GIT-REPO-URL, then click on `advanced options`.

>  If you skipped creating your own repo you should use the one we have provided [here]({{ GIT_URL }}/user99/inventory-dotnet-core.git) otherwise your repo should look like this one: {{ GIT_URL }}/userXX/inventory-dotnet-core.git

![Deploying on OCP]({% image_path dotnet-deploy-api-03.png %}){:width="600px"}

Once in the advanced options screen, you'll see that both `Name` and `Git Repository URL` are already populated. Scroll down until you see an area called `Build Configuration`.

![Deploying on OCP]({% image_path dotnet-deploy-api-04.png %}){:width="740px"}

In this area we're going to add an environment variable which holds the path to the C# project. This variable will be used while building the application artifacts and the image to run the code, not at run time.

* **Environment Variable:** DOTNET_STARTUP_PROJECT
* **Value:** src/CoolstoreXX.Inventory/CoolstoreXX.Inventory.csproj

![Deploying on OCP]({% image_path dotnet-deploy-api-05.png %}){:width="740px"}

Leave all the other default values as they are and click `Create`

![Deploying on OCP]({% image_path dotnet-deploy-api-06.png %}){:width="740px"}

Click on `Continue to the project Overview` 

![Deploying on OCP]({% image_path dotnet-deploy-api-07.png %}){:width="740px"}

You should see how the Build task progesses and evetually how the pod is started and ready to receive requests.

![Deploying on OCP]({% image_path dotnet-deploy-api-08.png %}){:width="740px"}

Once the pod is ready, it's color changes to bright blue. Now you can click on the link and hopefully you'll find an API test page.

![Deploying on OCP]({% image_path dotnet-deploy-api-09.png %}){:width="740px"}

#### Testing our .Net Core API on OpenShift

As we have done before, you can use the API tests page.

![Deploying on OCP]({% image_path dotnet-deploy-api-10.png %}){:width="740px"}

Please do also test `/metrics` so that you're sure monitoring is in place.

#### Activating monitoring for our application

Hopefully your code is up and running and monitoring is, well, monitoring :-)

But... there're a couple of steps needed to make Prometheus aware of your application `/metrics` endpoint.

First you have to modify the DeploymentConfig you just created.

![Adapting DeploymentConfig]({% image_path dotnet-prometheus-annotations-deployment-yaml-01.png %}){:width="740px"}

![Adapting DeploymentConfig]({% image_path dotnet-prometheus-annotations-deployment-yaml-02.png %}){:width="740px"}

![Adapting DeploymentConfig]({% image_path dotnet-prometheus-annotations-deployment-yaml-03.png %}){:width="740px"}

Add the following `prometheus.io` annotations. **Please pay attention** to the location of these annotations: **spec/template/metadata/annotations**

~~~yaml
...
spec:
  template:
    metadata:
      annotations:
        ...
        prometheus.io/path: /metrics
        prometheus.io/port: "8080"
        prometheus.io/scrape: "true"
...
~~~

Now let's do something similar in the Service definition.

![Adapting Service]({% image_path dotnet-prometheus-annotations-service-yaml-01.png %}){:width="740px"}

![Adapting Service]({% image_path dotnet-prometheus-annotations-service-yaml-02.png %}){:width="740px"}

![Adapting Service]({% image_path dotnet-prometheus-annotations-service-yaml-03.png %}){:width="740px"}

Add the following `prometheus.io` annotations. **Please pay attention** now the location is: **metadata/annotations**

~~~yaml
apiVersion: v1
kind: Service
metadata:
  annotations:
    ...
    prometheus.io/scrape: "true"
    prometheus.io/scheme: http
    prometheus.io/port: "8080"
...
~~~

Ask your instructor to have a look to [this](https://prometheus-application-monitoring.13.95.86.47.nip.io/targets) url and check if you find your application!
**https://prometheus-application-monitoring.13.95.86.47.nip.io/targets**

Well done! You are ready to move on to the next lab.
