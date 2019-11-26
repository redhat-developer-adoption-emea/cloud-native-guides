## Getting Started with OpenShift

In this lab you will get familiar with the OpenShift CLI and OpenShift Web Console and get ready for the Cloud Native Workshop labs.

For completing the following labs, you can either use your own workstation or as an alternative, Eclipse Che web IDE. The advantage of your own workstation is that you use the  environment that you are familiar with while the advantage of Eclipse Che is that all tools needed (Maven, Git, OpenShift CLI, etc ) are pre-installed in it (not on your workstation!) and all interactions takes place within the browser which removes possible internet speed issues and version incompatibilities on your workstation.

The choice is yours but whatever you pick, like most things in life, stick with it for all the labs. We 
ourselves are in love with Eclipse Che and highly recommend it.

## Setup Your Workspace on Eclipse Che

Follow these instructions to setup the development environment on Eclipse Che. 

You might be familiar with the MS Visual Studio Code which is one of the most popular IDEs. [Eclipse Che](https://www.eclipse.org/che/) is a next-generation Web IDE which is web-based and gives you a full-featured IDE running in the cloud. You have an Eclipse Che instance deployed on your OpenShift cluster which you will use during these labs.

Go to the [Eclipse Che url]({{ ECLIPSE_CHE_URL }}) in order to configure your development workspace: {{ ECLIPSE_CHE_URL }}

First, you need to log in as an OpenShift user. Use the same username and password you've been provided with.

![Eclipse Che - Log in]({% image_path bootstrap-che-1.png %}){:width="700px"}

Second, click on `Allow selected permissions`.

![Eclipse Che - Register]({% image_path bootstrap-che-2.png %}){:width="700px"}

Third, you need to register as a Che user. Register and choose the same username and password as your OpenShift credentials.

![Eclipse Che - Register]({% image_path bootstrap-che-3.png %}){:width="700px"}

You can now create your workspace based on a stack. A stack is a template of workspace configuration. For example, it includes the programming language and tools needed in your workspace. Stacks make it possible to recreate identical workspaces with all the tools and needed configuration on-demand. 

For this lab, right-click [here]({{ ECLIPSE_CHE_URL }}/f?url=https://github.com/cvicens/inventory-api-1st-maven) and open in a new tab and Eclipse Che will take it from there.

Eclipse Che will look for a file called `devfile.yaml` like the next one.

> There are two main sections in this `devfile`:
> 
> * **projects:** where you define which git repos you want to include in your workspace
> * **components:** where you define plugins and containers you need

~~~yaml
---
apiVersion: 1.0.0
metadata:
  generateName: inventory-maven-
projects:
  - name: inventory-maven
    source:
      location: 'https://github.com/cvicens/inventory-api-1st-maven.git'
      type: git
      branch: master
components:
  - id: redhat/java/latest
    memoryLimit: 1512Mi
    type: chePlugin
  - mountSources: true
    endpoints:
      - name: 8080/tcp
        port: 8080
    memoryLimit: 768Mi
    type: dockerimage
    volumes:
      - name: m2
        containerPath: /home/user/.m2
    alias: tools
    image: 'quay.io/cvicensa/cnw-che-stack:7.3.1-3'
    env:
      - value: http://nexus.lab-infra:8081/repository/maven-all-public
        name: MAVEN_MIRROR_URL
      - value: /home/user/.m2
        name: MAVEN_CONFIG
      - value: >-
          -XX:MaxRAMPercentage=50 -XX:+UseParallelGC -XX:MinHeapFreeRatio=10
          -XX:MaxHeapFreeRatio=20 -XX:GCTimeRatio=4
          -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true
          -Xms20m -Djava.security.egd=file:/dev/./urandom -Duser.home=/home/user
        name: MAVEN_OPTS
      - value: >-
          -XX:MaxRAMPercentage=50 -XX:+UseParallelGC -XX:MinHeapFreeRatio=10
          -XX:MaxHeapFreeRatio=20 -XX:GCTimeRatio=4
          -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true
          -Xms20m -Djava.security.egd=file:/dev/./urandom
        name: JAVA_OPTS
      - value: >-
          -XX:MaxRAMPercentage=50 -XX:+UseParallelGC -XX:MinHeapFreeRatio=10
          -XX:MaxHeapFreeRatio=20 -XX:GCTimeRatio=4
          -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true
          -Xms20m -Djava.security.egd=file:/dev/./urandom
        name: JAVA_TOOL_OPTIONS
~~~

Once you click on the link provided before, you'll land in Che and the process of creation or your workspace will start.

![Eclipse Che Workspace]({% image_path bootstrap-che-4.png %}){:width="700px"}

After some seconds you'll see the process progress.

![Eclipse Che Workspace]({% image_path bootstrap-che-5.png %}){:width="700px"}

A while later you should be ready to start working in your workspace.

![Eclipse Che Workspace]({% image_path bootstrap-che-6.png %}){:width="700px"}

If you click on the documents icon on the left you'll open the file explorer. If all according to the plan you should have a folder named `inventory-maven`. This folder contains the code you need to start developing the Inventory Service. The instructor could ask you to start there instead of doing labs 2 and 3.

![Eclipse Che Workspace]({% image_path bootstrap-che-7.png %}){:width="700px"}

Now open a terminal window to check everything is alright. Click on `Terminal` then on `Open Terminal on specific container`.

![Eclipse Che Terminal]({% image_path bootstrap-che-terminal-1.png %}){:width="700px"}

Then click on `tools`.

> **Notice** that the name of the container corresponds to the `alias` atribute in the `devfile`

![Eclipse Che Terminal]({% image_path bootstrap-che-terminal-2.png %}){:width="700px"}

Finally type `oc version`, you should see something similar to this.

## Explore OpenShift with OpenShift CLI

In order to login, we will use the `oc` command and then specify the server that we
want to authenticate to.

Issue the following command in Eclipse Che terminal and replace `{{OPENSHIFT_CONSOLE_URL}}` 
with your OpenShift Web Console url. 

~~~shell
$ oc login {{OPENSHIFT_CONSOLE_URL}}
~~~

You may see the following output:

~~~shell
The server uses a certificate signed by an unknown authority.
You can bypass the certificate check, but any data you send to the server could be intercepted by others.
Use insecure connections? (y/n):
~~~

Enter in `Y` to use a potentially insecure connection.  The reason you received
this message is because we are using a self-signed certificate for this workshop, but we did not provide you with the CA certificate that was generated by OpenShift. In a real-world scenario, either OpenShift's certificate would be
signed by a standard CA (eg: Thawte, Verisign, StartSSL, etc.) or signed by a
corporate-standard CA that you already have installed on your system.

Enter the username and password provided to you by the instructor.

Congratulations, you are now authenticated to the OpenShift server.

[Projects]({{OPENSHIFT_DOCS_BASE}}/architecture/core_concepts/projects_and_users.html#projects) are a top level concept to help you organize your deployments. An OpenShift project allows a community of users (or a user) to organize and manage their content in isolation from other communities. Each project has its own resources, policies (who can or cannot perform actions), and constraints (quotas and limits on resources, etc). Projects act as a "wrapper" around all the application services and endpoints you (or your teams) are using for your work.

For this lab, let's create a project that you will use in the following labs for 
deploying your applications. 

> Make sure to follow your instructor guidance on the project names in order to have a unique project name for yourself e.g. appending your username to the project name

~~~shell
$ oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}

Now using project "{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}" on server ...
...
~~~

OpenShift ships with a web-based console that will allow users to perform various tasks via a browser.  To get a feel for how the web console works, open your browser and go to the OpenShift Web Console.


The first screen you will see is the authentication screen. Enter your username and password and then log in. After you have authenticated to the web console, you will be presented with a list of projects that your user has permission to work with. 

Click on the **{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}** project to be taken to the project overview page which will list all of the routes, services, deployments and pods that you have running as part of your project. There's nothing there now, but that's about to change.

Now you are ready to get started with the labs!
