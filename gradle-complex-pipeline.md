## Creating gradle-ready Jenkins Slave Pods

This lab is part of a, more to the point, set of labs with regards to CI/CD, you can find the original version [here](https://github.com/openshift-labs/devops-guides).

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
FROM registry.access.redhat.com/openshift3/jenkins-agent-maven-35-rhel7:v3.10

MAINTAINER Siamak Sadeghianfar <ssadeghi@redhat.com>

ENV GRADLE_VERSION=4.8.1

USER root

RUN curl -skL -o /tmp/gradle-bin.zip https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip && \
    mkdir -p /opt/gradle && \
    unzip -q /tmp/gradle-bin.zip -d /opt/gradle && \
    ln -sf /opt/gradle/gradle-$GRADLE_VERSION/bin/gradle /usr/local/bin/gradle

RUN chown -R 1001:0 /opt/gradle && \
    chmod -R g+rw /opt/gradle

USER 1001
~~~

#### Building our custom slave image

**WARNING:**

> * **`{{COOLSTORE_PROJECT}}`** should be **`{{COOLSTORE_PROJECT}}-XX`**
> * **`{{COOLSTORE_PROJECT}}-dev`** should be **`{{COOLSTORE_PROJECT}}-dev-XX`**

If you haven't created projects `{{COOLSTORE_PROJECT}}` and `{{COOLSTORE_PROJECT}}-dev`, please do it as follows.

~~~shell
$ oc new-project {{COOLSTORE_PROJECT}}
$ oc new-project {{COOLSTORE_PROJECT}}-dev
~~~

You can build the docker image on OpenShift by creating a new build from the Git repository that contains the Dockerfile. OpenShift automatically detects the Dockerfile in the Git repository, builds an image from it and pushes the image into the OpenShift integrated image registry:

~~~shell
$ oc new-build -n {{COOLSTORE_PROJECT}} https://github.com/redhat-developer-adoption-emea/cloud-native-labs.git#{{ GITHUB_REF }} --context-dir=solutions/lab-12 --name=jenkins-slave-gradle-rhel7 
$ oc logs -n {{COOLSTORE_PROJECT}} -f bc/jenkins-slave-gradle-rhel7 
~~~

You can verify that an image stream is created in the **`{{COOLSTORE_PROJECT}}`** for the Jenkins Gradle slave image:

~~~shell
$ oc get is -n {{COOLSTORE_PROJECT}}

NAME                         DOCKER REPO                                     TAGS      
jenkins-agent-maven-35-rhel7   docker-registry.default.svc:5000/{{COOLSTORE_PROJECT}}/jenkins-agent-maven-35-rhel7   v3.10     About a minute ago
jenkins-slave-gradle-rhel7     docker-registry.default.svc:5000/{{COOLSTORE_PROJECT}}/jenkins-slave-gradle-rhel7     latest    12 seconds ago
~~~
 
The image is ready in the registry and all is left is to add metadata to the image stream so that Jenkins master can discover this new slave image by assigning the label `role=jenkins-slave` to the image and also optionally annotate it with `slave-label=gradle` to specify the slave name which is by default the name of the image.

~~~shell
$ oc label -n {{COOLSTORE_PROJECT}} is/jenkins-slave-gradle-rhel7 role=jenkins-slave
$ oc annotate -n {{COOLSTORE_PROJECT}} is/jenkins-slave-gradle-rhel7 slave-label=gradle
~~~

When Jenkins master starts for the first time, it automatically scans the image registry for slave images and configures them on Jenkins. Since you use an ephemeral Jenkins (without persistent storage) in this lab, restarting Jenkins causes a fresh Jenkins container to be deployed and to run the automatic configuration and discovery at startup to configure the Gradle slave image. When using a persistent Jenkins, all configurations would be kept and be available on the new container as well and therefore the automatic scan would not get triggered to avoid overwriting user configurations in Jenkins. In that case, you can configure the Gradle jenkins slave by adding a *Kubernetes Pod Template* in Jenkins configuration panel.

First of all let's deploy Jenkins with ephemeral storage.

> **Pay attention** to the next command that shows the templates available for Jenkins
>
> ~~~shell
> $ oc get template -n openshift | grep jenkins
> jenkins-ephemeral  Jenkins service, without persistent storage....                                    7 (all set)       6
> jenkins-persistent  Jenkins service, with persistent storage....                                       8 (all set)       7
> ~~~
>

Now let's deploy Jenkins with ephemeral storage.

~~~shell
$  oc new-app -n {{COOLSTORE_PROJECT}} --template=jenkins-ephemeral
--> Deploying template "openshift/jenkins-ephemeral" to project {{COOLSTORE_PROJECT}}

     Jenkins (Ephemeral)
     ---------
     Jenkins service, without persistent storage.
     
     WARNING: Any data stored will be lost upon pod destruction. Only use this template for testing.
     
    ...
    service "jenkins" created
--> Success
    Access your application via route 'jenkins-coolstore.apps.cloud-native.openshiftworkshop.com' 
    Run 'oc status' to view your app.
~~~

> If we had deployed Jenkins before in this project we should delete the Jenkins pod so that OpenShift auto-healing capability starts a new Jenkins pod:
> 
>	$ oc delete pod -l name=jenkins
>	

When Jenkins is up and running, you can login into Jenkins using your OpenShift credentials then *Manage Jenkins -> Configure System*. Scroll down to the Kubernetes section and notice that there is a Kubernetes Pod Template defined automatically for the Gradle slave image your created.

Your configuration should resemble this one.

![Kubernetes Pod Template]({% image_path devops-slave-pod-template.png %}){:width="500px"}

You can instruct Jenkins to run a pipeline using a specific slave image by specifying the slave label in the `node` step. The slave image label is either the image name or if specified, the value of `slave-label` annotation on the image stream. The following is a simple pipeline definition that clones our new Inventory service from the Git repository and then builds it using Gradle:

~~~yaml
apiVersion: build.openshift.io/v1
kind: BuildConfig
metadata:
  name: gradle-pipeline-simple
spec:
  source:
    type: None
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        pipeline {
          agent {
            label 'gradle'
          }

          stages {
            stage('Build') {
              steps {
                git url: "{{GIT_URL}}", branch: "{{GITHUB_REF}}"
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
    type: JenkinsPipeline
~~~

**OPTIONAL:** Create an OpenShift Pipeline that embeds this simple pipeline. Click on *Add to project* in the CI/CD Infra project and then *Import YAML/JSON*. Paste the YAML descriptor in the text field and then click on *Create*. Finally start this pipeline.

#### Building a pipe-line leveraging our custom Jenkins slave

Now we're going to create an OpenShift Pipeline that embeds a pipeline definition that builds our app using `gradle`. These are the steps that this pipeline conprehends:

, test it, builds an image using a binary file (`ROOT.jar`), ... , deploy the app and promote the image to our dev environment `{{COOLSTORE_PROJECT}}-dev`.

* Checkout: from the git repository
* Build: building the jar file (binary asset) using gradle
* Test
* Sonar: scan sources using Sonarqube
* Nexus: push our jar file to Nexus 3
* Build Image: triggers the build of a Java based image from the binary file built previously
* Approve: manually approve/reject from Jenkins
* Promote to DEV: tag image as `dev` ready which triggers the (re)deployment of the new image

> The next pipeline (or to be precise Jenkins' service account in project `{{COOLSTORE_PROJECT}}`) needs to be able to `edit` and `view` contents in project `{{COOLSTORE_PROJECT}}-dev`. 
> Additionally the default service account in project `{{COOLSTORE_PROJECT}}-dev` needs to be able to pull an image from an image stream in project `{{COOLSTORE_PROJECT}}`, this means we have to add this role `system:image-puller` to this service account `system:serviceaccount:coolstore-dev:default`

Please run this commands to fulfill the requisites referred to above.

~~~ shell
$ oc policy add-role-to-user edit system:serviceaccount:{{COOLSTORE_PROJECT}}:jenkins -n {{COOLSTORE_PROJECT}}-dev
$ oc policy add-role-to-user view system:serviceaccount:{{COOLSTORE_PROJECT}}:jenkins -n {{COOLSTORE_PROJECT}}-dev
$ oc policy add-role-to-user system:image-puller system:serviceaccount:{{COOLSTORE_PROJECT}}-dev:default -n {{COOLSTORE_PROJECT}}
~~~

Now it's time to create the pipeline, to do so please run the next commands. Review the next note to adapt the following variables to your environment.

> **NOTE:** 
>
> * **Subtitute USER_NUMBER="XX" by your assigned number!**
> * Ask your instructor to go to sonar Administration/Security/Users and get a proper token for SONAR_TOKEN variable, then substitute 'ASK_YOUR_INSTRUCTOR' with it.
> * Assign a proper value to APP_BASE, it should like the Openshift master url ***{{OPENSHIFT_CONSOLE_URL}}*** replacing `master` by `apps`
> 

~~~shell
$ export MY_USER_NUMBER="XX"
$ export SONAR_TOKEN="ASK_YOUR_INSTRUCTOR"
$ export APP_BASE="ASK_YOUR_INSTRUCTOR"
$ export GIT_URL="{{LABS_GIT_REPO}}"
$ export GIT_REF="{{GITHUB_REF}}"
~~~

**REMEMBER:**

> * **`{{COOLSTORE_PROJECT}}`** should be **`{{COOLSTORE_PROJECT}}-XX`**
> * **`{{COOLSTORE_PROJECT}}-dev`** should be **`{{COOLSTORE_PROJECT}}-dev-XX`**

~~~shell
$ cat << EOF | oc create -n "{{COOLSTORE_PROJECT}}-${MY_USER_NUMBER}" -f -
apiVersion: v1
kind: BuildConfig
metadata:
  name: gradle-pipeline-complex
spec:
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        // Don't forget to run the commands to create the dev project, and grant the needed roles to the service accounts

        def APP_BASE = "${APP_BASE}"

        def APP_NAME = "inventory"
        def APP_VERSION = "0.0.1-SNAPSHOT"

        def PROJECT_NAME = "{{COOLSTORE_PROJECT}}-${MY_USER_NUMBER}"
        def DEV_PROJECT_NAME = "{{COOLSTORE_PROJECT}}-dev-${MY_USER_NUMBER}"

        def GIT_URL = "${GIT_URL}"
        def GIT_REF = "${GIT_REF}"
        def CONTEXT_DIR = "inventory-spring-boot-gradle"

        def NEXUS = "http://nexus-lab-infra." + APP_BASE
        def NEXUS_USERNAME = "admin"
        def NEXUS_PASSWORD = "admin123"
        def NEXUS_PATH = "com/redhat/cloudnative/inventory"

        def SONAR = "http://sonarqube-lab-infra." + APP_BASE
        def SONAR_TOKEN = "${SONAR_TOKEN}"
        
        def BUILD_NAME = APP_NAME
        def BUILD_IMAGE_STREAM = "openshift/redhat-openjdk18-openshift:1.4"
            
        pipeline {
          agent {
            label 'gradle'
          }
          stages {
            stage('Checkout') {
              steps {
                git url: "\${GIT_URL}", branch: "\${GIT_REF}"
              }
            }
            
            stage('Build') {
                steps {
                    dir("\${CONTEXT_DIR}") {
                        sh "java -version"
                        sh "./gradlew build --no-daemon -x test"
                    }
                }
            }
            
            stage('Test') {
                steps {
                    dir("\${CONTEXT_DIR}") {
                        sh "./gradlew test --no-daemon"
                    }
                }
            }
            
            stage('Sonar') {
                steps {
                    script {
                        dir("\${CONTEXT_DIR}") {
                          sh "./gradlew sonarqube -Dsonar.host.url=\${SONAR} -Dsonar.login=\${SONAR_TOKEN} --no-daemon"
                        }
                    }
                }
            }
            
            stage('Nexus') {
                steps {
                    script {
                      dir("\${CONTEXT_DIR}") {
                        APP_VERSION = sh (script: './gradlew -q getVersion --no-daemon', returnStdout: true).trim()
                        sh "curl -v -u \${NEXUS_USERNAME}:\${NEXUS_PASSWORD} --upload-file ./build/libs/\${APP_NAME}-\${APP_VERSION}.jar \${NEXUS}/repository/maven-snapshots/\${NEXUS_PATH}/\${APP_VERSION}/\${APP_NAME}-\${APP_VERSION}.jar"
                      }
                    }
                }
            }
                                
            stage('Create Image Builder') {
              when {
                expression {
                  openshift.withCluster() {
                    return !openshift.selector("bc", "\${BUILD_NAME}").exists();
                  }
                }
              }
              steps {
                script {
                  openshift.withCluster() {
                    openshift.newBuild("--name=\${BUILD_NAME}", "--image-stream=\${BUILD_IMAGE_STREAM}", "--binary")
                  }
                }
              }
            }

            stage('Build Image') {
              steps {
                script {
                  dir("\${CONTEXT_DIR}") {
                    openshift.withCluster() {
                        openshift.selector("bc", "\${BUILD_NAME}").startBuild("--from-file=./build/libs/\${APP_NAME}-\${APP_VERSION}.jar", "--wait")
                    }      
                  }
                }
              }
            }

            stage('Approve') {
                steps {
                    timeout(time:15, unit:'MINUTES') {
                        input message:'Approve Deploy to Dev?'
                    }
                }
            }

            stage('Promote to DEV') {
              steps {
                script {
                  openshift.withCluster() {
                    openshift.tag("\${BUILD_NAME}:latest", "\${BUILD_NAME}:dev")
                  }
                }
              }
            }

            stage('Create DEV') {
              when {
                expression {
                  openshift.withCluster() {
                      openshift.withProject("\${DEV_PROJECT_NAME}") {
                        return !openshift.selector('dc', "\${APP_NAME}").exists()
                      }
                  }
                }
              }
              steps {
                script {
                  openshift.withCluster() {
                    openshift.withProject("\${DEV_PROJECT_NAME}") {
                        openshift.newApp("\${PROJECT_NAME}/\${BUILD_NAME}:dev", "--name=\${APP_NAME}").narrow('svc').expose()
                    }
                  }
                }
              }
            }
          }
        }
    type: JenkinsPipeline
EOF
~~~

Now it's time to start our pipe-line, we can do this either from the CLI.

~~~shell
$ oc start-build bc/karma-pipeline-complex -n {{COOLSTORE_PROJECT}}
build "karma-pipeline-complex-5" started
~~~

Or from the web-console, **Builds ➡ Pipelines**

![Pipeline Log]({% image_path devops-start-build-gradle-pipeline.png %}){:width="740px"}

After a successful pipeline built we should be able to run the following curl test with success.

~~~shell
$ curl http://inventory-{{COOLSTORE_PROJECT}}-dev-${MY_USER_NUMBER}.${APP_BASE}/api/inventory
[{"itemId":"329299","quantity":35},{"itemId":"329199","quantity":12},{"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},{"itemId":"165954","quantity":43},{"itemId":"444434","quantity":32},{"itemId":"444435","quantity":53}]
~~~