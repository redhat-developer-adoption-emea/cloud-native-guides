## Microservices with .NET Core 2.x

In this lab you will learn about **how to deploy and use a local Nuget server** 

Based on [Using a local NuGet server with Red Hat OpenShift](https://developers.redhat.com/blog/2019/01/08/local-nuget-server-red-hat-openshift-container-platform/).

#### What is Nuget?

...

#### Preprequisites

In order to follow this lab you'll need:
...

#### Adding .Net Core 2.2

Edit openshift is/dotnet substitute

~~~yaml
- annotations:
        description: >-
          Build and run .NET Core applications on RHEL 7. For more information
          about using this builder image, including OpenShift considerations,
          see
          https://github.com/redhat-developer/s2i-dotnetcore/tree/master/2.1/build/README.md.


          WARNING: By selecting this tag, your application will automatically
          update to use the latest version of .NET Core available on OpenShift,
          including major versions updates.
        iconClass: icon-dotnet
        openshift.io/display-name: .NET Core (Latest)
        sampleContextDir: app
        sampleRef: dotnetcore-2.1
        sampleRepo: 'https://github.com/redhat-developer/s2i-dotnetcore-ex.git'
        supports: dotnet
        tags: 'builder,.net,dotnet,dotnetcore'
      from:
        kind: ImageStreamTag
        name: '2.1'
      generation: 1
      importPolicy: {}
      name: latest
      referencePolicy:
        type: Local
~~~

With

~~~yaml
- annotations:
        description: >-
          Build and run .NET Core 2.2 applications on RHEL 7. For more
          information about using this builder image, including OpenShift
          considerations, see
          https://github.com/redhat-developer/s2i-dotnetcore/tree/master/2.2/build/README.md.
        iconClass: icon-dotnet
        openshift.io/display-name: .NET Core 2.2
        sampleContextDir: app
        sampleRef: dotnetcore-2.2
        sampleRepo: 'https://github.com/redhat-developer/s2i-dotnetcore-ex.git'
        supports: 'dotnet:2.2,dotnet'
        tags: 'builder,.net,dotnet,dotnetcore,rh-dotnet22'
        version: '2.2'
      from:
        kind: DockerImage
        name: 'registry.redhat.io/dotnet/dotnet-22-rhel7:2.2'
      generation: 2
      importPolicy: {}
      name: '2.2'
      referencePolicy:
        type: Local
    - annotations:
        description: >-
          Build and run .NET Core applications on RHEL 7. For more information
          about using this builder image, including OpenShift considerations,
          see
          https://github.com/redhat-developer/s2i-dotnetcore/tree/master/2.2/build/README.md.


          WARNING: By selecting this tag, your application will automatically
          update to use the latest version of .NET Core available on OpenShift,
          including major versions updates.
        iconClass: icon-dotnet
        openshift.io/display-name: .NET Core (Latest)
        sampleContextDir: app
        sampleRef: dotnetcore-2.2
        sampleRepo: 'https://github.com/redhat-developer/s2i-dotnetcore-ex.git'
        supports: dotnet
        tags: 'builder,.net,dotnet,dotnetcore'
      from:
        kind: ImageStreamTag
        name: '2.2'
      generation: 1
      importPolicy: {}
      name: latest
      referencePolicy:
        type: Local
~~~

#### Installing Baget...

~~~shell
$ oc create -f https://raw.githubusercontent.com/redhat-developer/s2i-dotnetcore/master/templates/community/dotnet-baget.json -n openshift
template.template.openshift.io/dotnet-baget-persistent created
template.template.openshift.io/dotnet-baget-ephemeral created
~~~

~~~shell
$ oc new-app dotnet-baget-ephemeral -n lab-infra
--> Deploying template "openshift/dotnet-baget-ephemeral" to project lab-infra

     BaGet NuGet Package Server (ephemeral)
     ---------
     A lightweight NuGet service, see https://github.com/loic-sharma/BaGet.

     * With parameters:
        * Name=nuget
        * Upstream Package Source=https://api.nuget.org/v3/index.json
        * NuGet API Key=
        * Package deletion behavior=Unlist
        * Memory Limit=512Mi
        * .NET builder=dotnet:2.2
        * Namespace=openshift
        * Git Repository URL=https://github.com/loic-sharma/BaGet.git
        * Git Reference=v0.1.29-prerelease
        * Startup Project=src/BaGet
        * SDK Version=

--> Creating resources ...
    secret "nuget-apikey" created
    service "nuget" created
    imagestream.image.openshift.io "nuget" created
    buildconfig.build.openshift.io "nuget" created
    deploymentconfig.apps.openshift.io "nuget" created
--> Success
    Build scheduled, use 'oc logs -f bc/nuget' to track its progress.
    Application is not exposed. You can expose services to the outside world by executing one or more of the commands below:
     'oc expose svc/nuget' 
    Run 'oc status' to view your app.
~~~

See progress

~~~shell
$ oc logs -f bc/nuget
~~~

Now you can use DOTNET_RESTORE_SOURCES=http://nuget:8080/v3/index.json to speed up builds.