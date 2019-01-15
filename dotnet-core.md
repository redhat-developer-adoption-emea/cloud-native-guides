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

In ordet to generate the c# (.Net Core) code (stub/façade) we're going to use the [**Open API Generator**](https://github.com/openapitools/openapi-generator). These tool parses the YAML file you've hopefully retrieve and generates an REST API following the speficication, including the example provided, if any, for the different response actions defined.

~~~shell
$ mkdir inventory-dotnet-core-lab && cd inventory-dotnet-core-lab
$ mkdir ./bin
$ curl -L -o ./bin/openapi-generator-cli.jar http://central.maven.org/maven2/org/openapitools/openapi-generator-cli/3.3.4/openapi-generator-cli-3.3.4.jar
$ export OUTPUT_DIR="inventory-gen"
$ java -jar ./bin/openapi-generator-cli.jar generate -i Inventory\ API.yaml -g aspnetcore -o $OUTPUT_DIR
~~~

#### [Temporary] Fixing the error in DefaultApi.cs

[Open API Generator](https://github.com/openapitools/openapi-generator) generates a proper C# API server stub but introduces a couple of errors when copying the reponse examples provided within the specification.

These two errors are in `src/Org.OpenAPITools/Controllers/DefaultApi.cs` please follow the instructions below to fix them.

Locate the following piece of code in `DefaultApi.cs`, as you can see corresponds to the `GET` operation to get all the inventory items. 

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

> If you're curious the original JSON version of our specification is here `src/Org.OpenAPITools/wwwroot/openapi-original.json`


#### Testing the API

In order to test the API locally, you need to be in the folder where we have run the commands to generate the code. From that folder you should be able to run the following command.

> Pay attention to the `-p` flag, as you can see it points to the project file inside $OUTPUT_DIR. This environement variable was populated before with "inventory-gen"

~~~shell
$ dotnet run -p $OUTPUT_DIR/src/Org.OpenAPITools/Org.OpenAPITools.csproj
Hosting environment: Development
Content root path: /Users/cvicensa/Projects/openshift/aramco/inventory-dotnet-core/inventory-gen/src/Org.OpenAPITools
Now listening on: http://0.0.0.0:8080
Application started. Press Ctrl+C to shut down.
~~~

Let's test the `/api/inventory` path from another terminal window.

~~~shell
$ curl http://localhost:8080/api/inventory
[{"itemId":"329299","quantity":35},{"itemId":"329199","quantity":12},{"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},{"itemId":"165954","quantity":43},{"itemId":"444434","quantity":32},{"itemId":"444435","quantity":53}]
~~~

Finally let's test the `/api/inventory/{itemId}`.

~~~shell
$ curl http://localhost:8080/api/inventory/329299
{"itemId":"329299","quantity":35}
~~~

So far so good, now we have to create a git repo and push our code to it.

#### Creating a git repo for the generated code

You can use any Git server (e.g. GitHub, BitBucket, etc) for this lab but we have prepared a 
Gogs git server which you can access here: 

{{ GIT_URL }}

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
git config --global user.name "developer"
git config --global user.email "developer@me.com"
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

In order to deploy our API using the web console, open your `{{ COOLSTORE_PROJECT }}` and click on `Catalog` (bottom-left corner). Now choose `.Net Core`.

![Deploying on OCP]({% image_path dotnet-deploy-api-01.png %}){:width="740px"}

Click on `Next`.

![Deploying on OCP]({% image_path dotnet-deploy-api-02.png %}){:width="600px"}

Name your application as `inventory-dotnet-core` and copy the GIT-REPO-URL, then click on `advanced options`.

![Deploying on OCP]({% image_path dotnet-deploy-api-03.png %}){:width="600px"}

Once in the advanced options screen, you'll see that both `Name` and `Git Repository URL` are already populated. Scroll down until you see an area called `Build Configuration`.

![Deploying on OCP]({% image_path dotnet-deploy-api-04.png %}){:width="740px"}

In this area we're going to add an environment variable which holds the path to the C# project. This variable will be used while building the application artifacts and the image to run the code, not at run time.

* **Environment Variable:** DOTNET_STARTUP_PROJECT
* **Value:** src/Org.OpenAPITools/Org.OpenAPITools.csproj

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

You can use the API tests page.

![Deploying on OCP]({% image_path dotnet-deploy-api-10.png %}){:width="740px"}

Or instead you can run the tests as we did before but this time pointing to the exposed URL in Openshift.

*Get all the inventory items.*

~~~shell
$ curl http://inventory-dotnet-core-coolstore.apps.serverless.openshiftworkshop.com/api/inventory
[{"itemId":"329299","quantity":35},{"itemId":"329199","quantity":12},
{"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},
{"itemId":"165954","quantity":43},{"itemId":"444434","quantity":32},
{"itemId":"444435","quantity":53}]
~~~

*Get a given item by id.*

~~~shell
$ curl http://inventory-dotnet-core-coolstore.apps.serverless.openshiftworkshop.com/api/inventory/329299
{"itemId":"329299","quantity":35}
~~~

Well done! You are ready to move on to the next lab.
