## Creating karma-ready Jenkins Slave Pods

This lab is a spin-off of s set of labs regarding CI/CD, you can find the original version [here](https://github.com/openshift-labs/devops-guides).

Here we're going to use a custom Jenkins slave to build NodeJS + Angular 6 application running a pipeline which will run karma tests on a custom image where we have installed Chrome and NG.

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

Karma is one of the popular testing frameworks for front-end applications and although for quite some time PhantomJS was the defacto browser to run tests against nowdays it's usual to use real (headless) browsers.

In this lab we're going to use Chrome and because it's not added by default to the NodeJS slave image, we're going to create a new image based on that one. Here is the content of the [Dockerfile on GitHub](https://raw.githubusercontent.com/redhat-developer-adoption-emea/cloud-native-labs/ocp-3.10.karma/solutions/lab-13/Dockerfile) for building the NodeJS slave image:

~~~shell
#FROM registry.access.redhat.com/openshift3/jenkins-agent-nodejs-8-rhel7:v3.10
FROM openshift/jenkins-agent-nodejs-8-centos7:v3.10

MAINTAINER Carlos Vicens <cvicensa@redhat.com>

ENV PATH=/opt/rh/rh-nodejs8/root/usr/bin:${PATH}

USER root

COPY google-chrome.repo  /etc/yum.repos.d/google-chrome.repo

RUN yum install -y google-chrome-stable

USER 1001

RUN npm install -g @angular/cli@latest
~~~

#### Building our custom slave image

If you haven't created projects `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` and `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev`, please do it as follows.

~~~shell
$ oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
$ oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
~~~

You can build the docker image on OpenShift by creating a new build from the Git repository that contains the Dockerfile. OpenShift automatically detects the Dockerfile in the Git repository, builds an image from it and pushes the image into the OpenShift integrated image registry:

~~~shell
$ oc new-build -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} https://github.com/redhat-developer-adoption-emea/cloud-native-labs.git#{{ GITHUB_REF }} --context-dir=solutions/lab-13 --name=jenkins-slave-karma-centos7
$ oc logs -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} -f bc/jenkins-slave-karma-centos7
~~~

You can verify that an image stream is created in the **`{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`** for the Jenkins NodeJS 'karma' slave image:

~~~shell
$ oc get is -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
NAME                             DOCKER REPO                                                                    TAGS      UPDATED
jenkins-agent-nodejs-8-centos7   docker-registry.default.svc:5000/{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}/jenkins-agent-nodejs-8-centos7   v3.10     11 minutes ago
jenkins-slave-karma-centos7      docker-registry.default.svc:5000/{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}/jenkins-slave-karma-centos7      latest    3 minutes ago
~~~
 
The image is ready in the registry and all is left is to add metadata to the image stream so that Jenkins master can discover this new slave image by assigning the label `role=jenkins-slave` to the image and also optionally annotate it with `slave-label=karma` to specify the slave name which is by default the name of the image.

~~~shell
$ oc label -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} is/jenkins-slave-karma-centos7 role=jenkins-slave
$ oc annotate -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} is/jenkins-slave-karma-centos7 slave-label=karma
~~~

When Jenkins master starts for the first time, it automatically scans the image registry for slave images and configures them on Jenkins. Since you use an ephemeral Jenkins (without persistent storage) in this lab, restarting Jenkins causes a fresh Jenkins container to be deployed and to run the automatic configuration and discovery at startup to configure the karma slave image. When using a persistent Jenkins, all configurations would be kept and be available on the new container as well and therefore the automatic scan would not get triggered to avoid overwriting user configurations in Jenkins. In that case, you can configure the karma jenkins slave by adding a *Kubernetes Pod Template* in Jenkins configuration panel.

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
$  oc new-app -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} --template=jenkins-ephemeral
--> Deploying template "openshift/jenkins-ephemeral" to project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}

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

When Jenkins is up and running, you can login into Jenkins using your OpenShift credentials then *Manage Jenkins -> Configure System*. Scroll down to the Kubernetes section and notice that there is a Kubernetes Pod Template defined automatically for the karma slave image your created.

![Kubernetes Pod Template]({% image_path devops-karma-slave-pod-template.png %}){:width="500px"}

You can instruct Jenkins to run a pipeline using a specific slave image by specifying the slave label in the `node` step or in the `agent` step. The slave image label is either the image name or if specified, the value of `slave-label` annotation on the image stream. The following is a simple pipeline definition that clones our new Inventory service from the Git repository and then runs the `build` and `test` tasks using [npx](https://medium.com/@maybekatz/introducing-npx-an-npm-package-runner-55f7d4bd282b):

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
            label 'karma'
          }
          stages {
            stage('Build') {
              steps {
                git url: "{{GIT_URL}}", branch: "{{GITHUB_REF}}"
                dir('karma-tests') {
                    sh "npx ng build"
                }
              }
            }
            stage('Test') {
              steps {
                dir('karma-tests') {
                    sh "npx ng test"
                }
              }
            }
          }
        }
    type: JenkinsPipeline
~~~

**OPTIONAL:** Create an OpenShift Pipeline that embeds this simple pipeline. Click on *Add to project* in the CI/CD Infra project and then *Import YAML/JSON*. Paste the YAML descriptor in the text field and then click on *Create*. Finally start this pipeline.

#### Building a pipe-line leveraging our custom Jenkins slave

Now we're going to create an OpenShift Pipeline that embeds a pipeline definition that builds our app using `ng`, test it, builds an image, deploy the app and promote the image to our dev environment `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev`.

> The next pipeline (or to be precise Jenkins' service account in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`) needs to be able to `edit` and `view` contents in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev`. 
> Additionally the default service account in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev` needs to be able to pull an image from an image stream in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`, this means we have to add this role `system:image-puller` to this service account `system:serviceaccount:coolstore-dev:default`

Please run this commands to fulfill the requisites referred to above.

~~~ shell
$ oc policy add-role-to-user edit system:serviceaccount:{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}:jenkins -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
$ oc policy add-role-to-user view system:serviceaccount:{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}:jenkins -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
$ oc policy add-role-to-user system:image-puller system:serviceaccount:{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev:default -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
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

> * **`{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`** should be **`{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-XX`**
> * **`{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev`** should be **`{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev-XX`**

~~~shell
$ cat << EOF | oc create -n "{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-${MY_USER_NUMBER}" -f -
apiVersion: v1
kind: BuildConfig
metadata:
  name: karma-pipeline-complex
spec:
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        // Don't forget to run the commands to create the dev project, and grant the needed roles to the service accounts

        def APP_BASE = "${APP_BASE}"

        def APP_NAME = "karma-tests"
        def APP_VERSION = "0.0.1-SNAPSHOT"

        def PROJECT_NAME = "{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}" + "\${MY_USER_NUMBER}"
        def DEV_PROJECT_NAME = "{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev" + "\${MY_USER_NUMBER}"

        def GIT_URL = "${GIT_URL}"
        def GIT_REF = "${GIT_REF}"
        def CONTEXT_DIR = "karma-tests"

        def NEXUS = "http://nexus-lab-infra." + APP_BASE
        def NEXUS_USERNAME = "admin"
        def NEXUS_PASSWORD = "admin123"
        def NEXUS_PATH = "com/redhat/cloudnative/inventory"

        def SONAR = "http://sonarqube-lab-infra." + APP_BASE
        def SONAR_TOKEN = "${SONAR_TOKEN}"

        def BUILD_NAME = APP_NAME
        def BUILD_IMAGE_STREAM = "openshift/nodejs:8"
            
        pipeline {
          agent {
            label 'karma'
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
                        sh "export NPM_CONFIG_PREFIX=~/.npm-global"
                        sh "npm install"
                        sh "npx ng build"
                        sh "tar cvf ./dist/\${APP_NAME}-\${APP_VERSION}.tar -C ./dist/karma-tests/ ."
                    }
                }
            }
            
            stage('Test') {
                steps {
                    dir("\${CONTEXT_DIR}") {
                        sh "npx ng test"
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
                    openshift.newBuild("\${BUILD_IMAGE_STREAM}~\${GIT_URL}#\${GIT_REF}", "--name=\${BUILD_NAME}", "--context-dir=\${CONTEXT_DIR}")
                  }
                }
              }
            }

            stage('Build Image') {
              steps {
                script {
                  dir("\${CONTEXT_DIR}") {
                    openshift.withCluster() {
                        openshift.selector("bc", "\${BUILD_NAME}").startBuild("--wait")
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
$ oc start-build bc/karma-pipeline-complex -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
build "karma-pipeline-complex-5" started
~~~

Or from the web-console, **Builds ➡ Pipelines**

![Pipeline Log]({% image_path devops-start-build-karma-tests-pipeline.png %}){:width="740px"}

After a successful pipeline built we should be able to visit our NodeJS application. Please go to {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev project and have a look to the Overview area, it should look like this. 

![Pipeline Log]({% image_path devops-karma-tests-overview.png %}){:width="740px"}

Once there, please click on the link.

![Pipeline Log]({% image_path devops-karma-tests-app-deployed.png %}){:width="740px"}
