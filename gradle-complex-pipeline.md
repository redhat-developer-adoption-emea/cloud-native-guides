## Creating Custom Jenkins Slave Pods

This lab is part of a more to the point with regards to CI/CD, you can find the original version [here](https://github.com/openshift-labs/devops-guides).

As a continuation of the previous lab we're going to use a custom Jenkins slave to build our new inventory service using [Gradle](https://gradle.org/).

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

After Maven, Gradle is one of the popular build tools for Java projects. Let’s build a new slave image to enable Jenkins to run Gradle builds.
Due to similarities between Maven and Gradle, the simplest way to start is to create a Dockerfile and build upon the Maven slave image. Here is the content of the [Dockerfile on GitHub](https://raw.githubusercontent.com/redhat-developer-adoption-emea/cloud-native-labs/ocp-3.10/solutions/lab-12/Dockerfile) for building the Gradle slave image:

~~~shell
FROM registry.access.redhat.com/openshift3/jenkins-slave-maven-rhel7

ENV GRADLE_VERSION=4.9

USER root

RUN curl -skL -o /tmp/gradle-bin.zip https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip && \
    mkdir -p /opt/gradle && \
    unzip -q /tmp/gradle-bin.zip -d /opt/gradle && \
    ln -sf /opt/gradle/gradle-$GRADLE_VERSION/bin/gradle /usr/local/bin/gradle

RUN chown -R 1001:0 /opt/gradle && \
    chmod -R g+rw /opt/gradle

USER 1001
~~~

You can build the docker image on OpenShift by creating a new build from the Git repository that contains the Dockerfile. OpenShift automatically detects the Dockerfile in the Git repository, builds an image from it and pushes the image into the OpenShift integrated image registry:

~~~shell
$ oc new-build https://github.com/redhat-developer-adoption-emea/cloud-native-labs.git#{{ GITHUB_REF }} --name=jenkins-slave-gradle-rhel7 --context-dir=solutions/lab-12
$ oc logs -f bc/jenkins-slave-gradle-rhel7 
~~~

You can verify that an image stream is created in the _CI/CD Infra_ project for the Jenkins Gradle slave image:

~~~shell
$ oc get is

NAME                         DOCKER REPO                                     TAGS      
jenkins-slave-gradle-rhel7   172.30.1.1:5000/{{COOLSTORE_PROJECT}}/jenkins-slave-gradle-rhel7   latest
jenkins-slave-maven-rhel7    172.30.1.1:5000/{{COOLSTORE_PROJECT}}/jenkins-slave-maven-rhel7    latest
~~~
 
The image is ready in the registry and all is left is to add metadata to the image stream so that Jenkins master can discover this new slave image by assigning the label `role=jenkins-slave` to the image and also optionally annotate it with `slave-label=gradle` to specify the slave name which is by default the name of the image.

~~~shell
$ oc label is/jenkins-slave-gradle-rhel7 role=jenkins-slave
$ oc annotate is/jenkins-slave-gradle-rhel7 slave-label=gradle
~~~

When Jenkins master starts for the first time, it automatically scans the image registry for slave images and configures them on Jenkins. Since you use an ephemeral Jenkins (without persistent storage) in this lab, restarting Jenkins causes a fresh Jenkins container to be deployed and to run the automatic configuration and discovery at startup to configure the Gradle slave image. When using a persistent Jenkins, all configurations would be kept and be available on the new container as well and therefore the automatic scan would not get triggered to avoid overwriting user configurations in Jenkins. In that case, you can configure the Gradle jenkins slave by adding a *Kubernetes Pod Template* in Jenkins configuration panel.

> If we had deployed Jenkins before in this project we should delete the Jenkins pod so that OpenShift auto-healing capability starts a new Jenkins pod:
> 
>	$ oc delete pod -l name=jenkins
>	

When Jenkins is up and running, you can login into Jenkins using your OpenShift credentials then *Manage Jenkins -> Configure System*. Scroll down to the Kubernetes section and notice that there is a Kubernetes Pod Template defined automatically for the Gradle slave image your created.

![Kubernetes Pod Template]({% image_path devops-slave-pod-template.png %}){:width="500px"}

You can instruct Jenkins to run a pipeline using a specific slave image by specifying the slave label in the `node` step. The slave image label is either the image name or if specified, the value of `slave-label` annotation on the image stream. The following is a simple pipeline definition that clones our new Inventory service from the Git repository and then builds it using Gradle:

~~~shell
pipeline {
  agent {
    label 'gradle'
  }
  stages {
    stage('Build') {
      steps {
        git url: "https://github.com/redhat-developer-adoption-emea/cloud-native-labs", branch: 'ocp-3.10'
        dir('inventory-spring-boot-gradle') {
            sh "./gradlew build"            
        }
      }
    }
    stage('Test') {
      steps {
        dir('inventory-spring-boot-gradle') {
            sh "./gradlew test"
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
  name: gradle-pipeline-complex
spec:
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        def GIT_URL = "https://github.com/redhat-developer-adoption-emea/cloud-native-labs"
        def GIT_REF = "ocp-3.10"
        def CONTEXT_DIR = "inventory-spring-boot-gradle"
        def NEXUS = "http://nexus-lab-infra.apps.istio.openshiftworkshop.com"
        def NEXUS_USERNAME = "admin"
        def NEXUS_PASSWORD = "admin123"
        def SONAR = "http://nexus-lab-infra.apps.istio.openshiftworkshop.com"
        def SONAR_TOKEN = "f7d35ff77556cdf83fc0b78311201c4a2dd7227b"5

        def version = ""
            
        pipeline {
          agent {
            label 'maven'
          }
          stages {
            stage('Checkout') {
              steps {
                git url: "${GIT_URL}", branch: "${GIT_REF}"
              }
            }
            
            stage('Build') {
                
                steps {
                    dir("${CONTEXT_DIR}") {
                        sh "java -version"
                        sh "./gradlew build --no-daemon"
                    }
                }
            }
            
            stage('Test') {
                steps {
                    dir("${CONTEXT_DIR}") {
                        sh "./gradlew test --no-daemon"
                    }
                }
            }
            
            stage('Nexus') {
                steps {
                    script {
                      def version
                      dir("${CONTEXT_DIR}") {
                        version = sh (script: './gradlew -q getVersion --no-daemon', returnStdout: true).trim()
                        sh "curl -v -u ${NEXUS_USERNAME}:${NEXUS_PASSWORD} --upload-file ./build/libs/inventory-${version}.jar ${NEXUS}/repository/maven-snapshots/com/redhat/cloudnative/inventory/${version}/inventory-${version}.jar"
                      }
                    }
                }
            }

            stage('Sonar') {
                steps {
                    dir("${CONTEXT_DIR}") {
                        ./gradlew sonarqube -Dsonar.host.url=${SONAR} -Dsonar.login=${SONAR_TOKEN}
                    }
                }
            }
          }
        }
    type: JenkinsPipeline
EOF
~~~

In the _CI/CD Infra_ project, click on *Builds -> Pipelines* on the left sidebar menu and then click on *Start Pipeline* button on the right side of *gradle-pipeline*. A new instance of the pipeline starts running using the Gradle slave image.

![Pipeline Log]({% image_path devops-slave-job-log.png %}){:width="740px"}

![OpenShift Pipeline with Gradle]({% image_path devops-slave-gradle-pipeline.png %}){:width="740px"}