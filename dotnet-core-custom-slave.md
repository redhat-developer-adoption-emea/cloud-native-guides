## Creating a custom .Net Core Jenkins Slave Pods

This lab is part of a more to the point with regards to CI/CD, you can find the original version [here](https://github.com/openshift-labs/devops-guides).

As a continuation of the previous lab we're going to use a custom Jenkins slave to build our new inventory service using [.Net Core](https://dotnet.microsoft.com/).

#### Background

Adding more CPU and memory to the Jenkins container helps to some extent, quite soon you would reach a breaking point which stops you from running more Jenkins builds. Fortunately Jenkins is built with scalability in mind and supports a master-slave architecture to allow running many simultaneous builds on slave nodes (workers) and allow Jenkins master to coordinate these builds. This distributed computing model will allow the Jenkins master to remain responsive to users, while offloading automation execution to the connected slave.

![Jenkins Master-Slave Architecture]({% image_path devops-slave-distributed-arch.png %}){:width="500px"}

This master-slave architecture also allows creating separate slaves with specific build tools installed such as Maven, NodeJS, etc instead of having all the build tools installed on the master Jenkins. The user can then instruct Jenkins master to run the build job on a specific slave that has the appropriate build tools and libraries installed.

The official Jenkins image provided by OpenShift includes the pre-installed [Kubernetes plug-in](https://wiki.jenkins-ci.org/display/JENKINS/Kubernetes%2BPlugin) that allows Jenkins slaves to be dynamically provisioned on multiple container hosts using Kubernetes and OpenShift.

To facilitate the use of the Kubernetes plug-in, OpenShift Container Platform provides three images suitable for use as Jenkins slaves:

* Base slave
* Maven slave
* NodeJS slave

The base image for Jenkins slaves pulls in both required tools (headless Java, the Jenkins JNLP client) and generally useful ones (including git, tar, zip, nss among others) as well as running the slave agent.

The certified Jenkins image provided by OpenShift also provides auto-discovery and auto-configuration of slave images by searching for these in the existing image streams within the project that it is running in. The search specifically looks for image streams that have the label role=jenkins-slave. When it finds an image stream with this label, it generates the corresponding Kubernetes plug-in configuration so you can assign your Jenkins jobs to run in a pod running the container image provided by the image stream.

Note that this scanning is only performed once, when the Jenkins master is starting.

The Red Hat Developer team has put together scripts and Dockerfiles to create Jenkins Slave images for .Net Core (both RHEL and Centos), you can find them [here](https://github.com/redhat-developer/dotnet-jenkins-slave). For the sake of simplicity we have borrowed those for version 2.2 and put them in folder `solutions/lab/lab-12-dotnet-core`. Here is the content of the [Dockerfile on GitHub](https://raw.githubusercontent.com/redhat-developer-adoption-emea/cloud-native-labs/{{ GITHUB_REF }}/solutions/lab-12-docker-core/Dockerfile) for building the Gradle slave image:

~~~shell
FROM openshift/jenkins-slave-base-centos7

# Labels consumed by Red Hat build service
LABEL com.redhat.component="rh-dotnet22-jenkins-slave-docker" \
      name="dotnet/dotnet-22-jenkins-slave-centos7" \
      version="2.2" \
      architecture="x86_64" \
      release="1" \
      io.k8s.display-name="Jenkins Slave .NET Core 2.2" \
      io.k8s.description="The jenkins slave dotnet image has the dotnet tools on top of the jenkins slave base image." \
      io.openshift.tags="openshift,jenkins,slave,dotnet,dotnet22"

# Don't download/extract docs for nuget packages
# Don't do initially populate of package cache
# Enable nodejs and dotnet scl
ENV DOTNET_CORE_VERSION=2.2 \
    BASH_ENV=/usr/local/bin/scl_enable \
    ENV=/usr/local/bin/scl_enable \
    PROMPT_COMMAND=". /usr/local/bin/scl_enable" \
    ENABLED_COLLECTIONS="rh-nodejs8 rh-dotnet22" \
    NUGET_XMLDOC_MODE=skip \
    DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

COPY contrib/bin/scl_enable /usr/local/bin/scl_enable

# Install
RUN yum install -y centos-release-dotnet centos-release-scl-rh && \
    INSTALL_PKGS="rh-dotnet22 rh-nodejs8-npm" && \
    yum install -y --setopt=tsflags=nodocs $INSTALL_PKGS && \
    rpm -V $INSTALL_PKGS && \
    yum clean all -y && \
# yum cache files may still exist (and quite large in size)
    rm -rf /var/cache/yum/*

RUN chown -R 1001:0 $HOME && \
    chmod -R g+rw $HOME

USER 1001
~~~

You can build the docker image on OpenShift by creating a new build from the Git repository that contains the Dockerfile. OpenShift automatically detects the Dockerfile in the Git repository, builds an image from it and pushes the image into the OpenShift integrated image registry:

~~~shell
$ oc new-build https://github.com/redhat-developer-adoption-emea/cloud-native-labs.git#{{ GITHUB_REF }} --name=jenkins-slave-dotnet-core-centos7 --context-dir=solutions/lab-12-dotnet-core
$ oc logs -f bc/jenkins-slave-dotnet-core-centos7
~~~

You can verify that an image stream is created in the _CI/CD Infra_ project for the Jenkins Gradle slave image:

~~~shell
$ oc get is
NAME                                DOCKER REPO                                                                       TAGS      UPDATED
jenkins-slave-base-centos7          docker-registry.default.svc:5000/{{COOLSTORE_PROJECT}}jenkins-slave-base-centos7          latest    2 hours ago
jenkins-slave-dotnet-core-centos7   docker-registry.default.svc:5000/{{COOLSTORE_PROJECT}}/jenkins-slave-dotnet-core-centos7   latest    17 minutes ago
~~~
 
The image is ready in the registry and all is left is to add metadata to the image stream so that Jenkins master can discover this new slave image by assigning the label `role=jenkins-slave` to the image and also optionally annotate it with `slave-label=dotnet` to specify the slave name which is by default the name of the image.

~~~shell
$ oc label is/jenkins-slave-dotnet-core-centos7 role=jenkins-slave
$ oc annotate is/jenkins-slave-dotnet-core-centos7 slave-label=dotnet
~~~

When Jenkins master starts for the first time, it automatically scans the image registry for slave images and configures them on Jenkins. Since you use an ephemeral Jenkins (without persistent storage) in this lab, restarting Jenkins causes a fresh Jenkins container to be deployed and to run the automatic configuration and discovery at startup to configure the Gradle slave image. When using a persistent Jenkins, all configurations would be kept and be available on the new container as well and therefore the automatic scan would not get triggered to avoid overwriting user configurations in Jenkins. In that case, you can configure the Gradle jenkins slave by adding a *Kubernetes Pod Template* in Jenkins configuration panel.

> If we had deployed Jenkins before in this project we should delete the Jenkins pod so that OpenShift auto-healing capability starts a new Jenkins pod:
> 
>	$ oc delete pod -l name=jenkins
>	

When Jenkins is up and running, you can login into Jenkins using your OpenShift credentials then *Manage Jenkins -> Configure System*. Scroll down to the Kubernetes section and notice that there is a Kubernetes Pod Template defined automatically for the Gradle slave image your created.

![Kubernetes Pod Template]({% image_path devops-slave-pod-template.png %}){:width="500px"}

You can instruct Jenkins to run a pipeline using a specific slave image by specifying the slave label in the `node` step. The slave image label is either the image name or if specified, the value of `slave-label` annotation on the image stream (`dotnet`). The following is a simple pipeline definition that clones an example application from a Github repository and then builds it using .Net Core:

~~~shell
pipeline {
  agent {
    label 'dotnet'
  }
  stages {
    stage('Build') {
      steps {
        git url: "https://github.com/redhat-developer/s2i-dotnetcore-ex", branch: 'master'
        dir('.') {
            sh "dotnet build app/app.csproj"
        }
      }
    }
    stage('Test') {
      steps {
        dir('.') {
            sh "dotnet test app/app.csproj"
        }
      }
    }
  }
}
~~~

Create an OpenShift Pipeline that embeds the above pipeline definition. Click on *Add to project* in the CI/CD Infra project and then *Import YAML/JSON*. Paste the following YAML script in the text field and then click on *Create*.

~~~shell
$ cat << EOF | oc create -f -
apiVersion: v1
kind: BuildConfig
metadata:
  name: dotnet-core-pipeline
spec:
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        pipeline {
          agent {
            label 'dotnet'
          }
          stages {
            stage('Build') {
              steps {
                git url: "https://github.com/redhat-developer/s2i-dotnetcore-ex", branch: 'master'
                dir('.') {
                    sh "dotnet build app/app.csproj"
                }
              }
            }
            stage('Test') {
              steps {
                dir('.') {
                    sh "dotnet test app/app.csproj"
                }
              }
            }
          }
        }
    type: JenkinsPipeline
EOF
~~~

In the _CI/CD Infra_ project, click on *Builds -> Pipelines* on the left sidebar menu and then click on *Start Pipeline* button on the right side of *dotnet-core-pipeline*. A new instance of the pipeline starts running using the .Net Core slave image.

![Pipeline Log]({% image_path devops-slave-job-log.png %}){:width="740px"}

![OpenShift Pipeline with Gradle]({% image_path devops-slave-gradle-pipeline.png %}){:width="740px"}


#### Local tests...

export DOTNET_STARTUP_PROJECT=src/Org.OpenAPITools
export DOTNET_FRAMEWORK=netcoreapp2.1
export DOTNET_CONFIGURATION=Release
export VERBOSITY_OPTION=
export DOTNET_APP_PATH=$(pwd)/build

dotnet restore "$DOTNET_STARTUP_PROJECT" $RESTORE_OPTIONS $VERBOSITY_OPTION
dotnet publish "$DOTNET_STARTUP_PROJECT" -f "$DOTNET_FRAMEWORK" -c "$DOTNET_CONFIGURATION" $VERBOSITY_OPTION \
         --self-contained false /p:PublishWithAspNetCoreTargetManifest=false --no-restore -o "$DOTNET_APP_PATH"

s2i build https://github.com/redhat-developer-adoption-emea/cloud-native-labs.git \
 -e DOTNET_STARTUP_PROJECT=src/Org.OpenAPITools/Org.OpenAPITools.csproj \
 --context-dir=solutions/lab-2-dotnet-core/inventory-dotnet-core \
 --ref=ocp-3.10 registry.redhat.io/dotnet/dotnet-21-rhel7 inventory-dotnet-core

~~~shell
oc new-build --binary=true dotnet:2.1 --name=inventory-dotnet-core-bin --binary=true \
 -e DOTNET_STARTUP_ASSEMBLY=Org.OpenAPITools.dll
--> Found image 9e62139 (6 weeks old) in image stream "openshift/dotnet" under tag "2.1" for "dotnet:2.1"

    .NET Core 2.1 
    ------------- 
    Platform for building and running .NET Core 2.1 applications

    Tags: builder, .net, dotnet, dotnetcore, rh-dotnet21

    * A source build using binary input will be created
      * The resulting image will be pushed to image stream tag "inventory-dotnet-core-bin:latest"
      * A binary build was created, use 'start-build --from-dir' to trigger a new build

--> Creating resources with label build=inventory-dotnet-core-bin ...
    imagestream.image.openshift.io "inventory-dotnet-core-bin" created
    buildconfig.build.openshift.io "inventory-dotnet-core-bin" created
--> Success
~~~

~~~shell
oc start-build inventory-dotnet-core-bin --from-dir=build --follow=true --wait=true
oc new-app inventory-dotnet-core-bin:latest
oc expose svc/inventory-dotnet-core-bin
~~~