## Alternative Inventory Service using Spring Boot + Gradle

In this lab you will learn about **how you can build microservices using** **Spring Boot + Gradle** and **Red Hat Openshift**. During this lab, you will use [**Open API Generator**](https://github.com/openapitools/openapi-generator), a code generator that can generate API client libraries, server stubs, documentation and configuration automatically given an [**Open API**](https://www.openapis.org/) Spec.

You will use a previously created Open API specification or you'll be given one by the instructor.

#### What is Gradle?

From ???:

> * 

Red Hat Openshift ... supports a 

You can find more information about .Net Core in RHEL [here](https://developers.redhat.com/products/dotnet/overview/)

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