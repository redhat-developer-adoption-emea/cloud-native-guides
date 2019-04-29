## Creating a complex Pipeline for our Inventory Service

This lab is part of a, more to the point, set of labs with regards to CI/CD, you can find the original version [here](https://github.com/openshift-labs/devops-guides).

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

#### Building a simple pipeline to proof we can use the Maven Slave
The following is a simple pipeline definition that clones our new Inventory service from the Git repository and then builds it using Gradle:

> You must substitute **GIT-REPO-URL** with your own Git repository URL as explained in lab **Alternative Inventory with Spring Boot + Maven**

~~~yaml
apiVersion: build.openshift.io/v1
kind: BuildConfig
metadata:
  name: inventory-spring-boot-maven-pipeline-simple
spec:
  source:
    type: None
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        pipeline {
          agent {
            label 'maven'
          }

          stages {
            stage('Build') {
              steps {
                git url: "GIT-REPO-URL", branch: "master"
                dir('.') {
                    sh "mvn package"  
                }
              }
            }
            
            stage('Test') {
              steps {
                dir('.') {
                    sh "mvn test"
                }
              }
            }
          }
        }
    type: JenkinsPipeline
~~~

**OPTIONAL:** Create an OpenShift Pipeline that embeds this simple pipeline. Click on **Add to project** in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` and then **Import YAML/JSON**. Paste the YAML descriptor in the text field and then click on **Create**. Finally **start** this pipeline. You can do this either clicking on `Build→Pipelines→Start Pipeline` or by executing: `oc  start-build inventory-spring-boot-maven-pipeline-simple`

> If you execute this simple pipeline (don't forget to use you GOGs repo url) you should see something like this at `Build→Pipelines`.
>
> **Notice** that Jenkins has been provisioned behind scenes just because you have created a pipeline by excuting this command: `oc get pod | grep jenkins`

![Jenkins Master-Slave Architecture]({% image_path inventory-spring-boot-maven-cicd-simple-execution.png %}){:width="740px"}

#### Building a complex pipeline

Now we're going to create an OpenShift Pipeline that embeds a pipeline definition that builds our app using `gradlew` (the Gradle Wrapper). These are the steps that this pipeline conprehends:

> DON'T FORGET to substitute `XX` by the number the instructor assigned to you at the begeinning of the workshop

* Checkout: from the git repository
* Build: building the jar file (binary asset) using gradle
* Test
* Sonar: scan sources using Sonarqube
* Nexus: push our jar file to Nexus 3
* Build Image: triggers the build of a Java based image from the binary file built previously
* Approve: manually approve/reject from Jenkins
* Promote to DEV: tag image as `dev` ready which triggers the (re)deployment of the new image

> **NOTE:** If you have already created a project named {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}} and another one {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev) you don't have to (and by the way you can't).
>
>~~~shell
$ oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
$ oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
~~~

> The next pipeline (or to be precise Jenkins' service account in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`) needs to be able to `edit` and `view` contents in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev`. 
> Additionally the default service account in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev` needs to be able to pull an image from an image stream in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`, this means we have to add this role `system:image-puller` to this service account `system:serviceaccounts:coolstore-dev:default`

Please run this commands to fulfill the requisites referred to above.

~~~ shell
$ oc policy add-role-to-user edit system:serviceaccount:{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}:jenkins -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
$ oc policy add-role-to-user view system:serviceaccount:{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}:jenkins -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
$ oc policy add-role-to-user system:image-puller system:serviceaccount:{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev:default -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
~~~

> **IMPORTANT:** Our pipeline will be using Nexus and we're going to need a file conteining some settings, please run the following commnand to create the needed file.
>
> Make sure you're at `inventory-spring-boot-maven/inventory-gen`. If in doubt review chapter **Generating the code**!

~~~shell
$ cat <<EOF > ./nexus_openshift_settings.xml
<?xml version="1.0"?>
<settings>
  <servers>
    <server>
      <id>nexus</id>
      <username>admin</username>
      <password>admin123</password>
    </server>
  </servers>
</settings>
EOF
~~~

Let's add this new file, commit and push it to the GitHub repository.

~~~shell
$ git add ./nexus_openshift_settings.xml
$ git commit -m "adding nexus_openshift_settings.xml"
$ git push -u origin master
~~~

Now (finally) it's time to create the pipeline, to do so please click on **Add to project** in project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` and then **Import YAML/JSON**. Paste the YAML descriptor in the text field and then click on **Create**.

> **NOTE: Before you run the next command take the next into account!** 
>
> * **Subtitute XX by your assigned number!**
> * Ask your instructor to go to sonar Administration/Security/Users and get a proper token for **SONAR_TOKEN** variable, then substitute '**ASK_YOUR_INSTRUCTOR**' with it.
> * Assign a proper value to **APP_BASE**, it should be like the Openshift master url ***{{OPENSHIFT_CONSOLE_URL}}*** replacing `master` by `apps`
> 

~~~yaml
apiVersion: v1
kind: BuildConfig
metadata:
  name: inventory-spring-boot-maven-pipeline-complex
spec:
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        // Don't forget to run the commands to create the dev project, and grant the needed roles to the service accounts
        
        // TODO: change needed
        def APP_BASE = "ASK_YOUR_INSTRUCTOR"

        def APP_NAME = "inventory"
        def APP_VERSION = "0.0.1-SNAPSHOT"

        // TODO: change needed
        def PROJECT_NAME = "{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}"
        def DEV_PROJECT_NAME = "{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev"

        // TODO: change needed
        def GIT_URL = "GIT-REPO-URL"
        def GIT_REF = "master"
        def CONTEXT_DIR = "."

        def NEXUS = "http://nexus-lab-infra." + APP_BASE
        def NEXUS_USERNAME = "admin"
        def NEXUS_PASSWORD = "admin123"
        def NEXUS_PATH = "com/redhat/cloudnative/inventory"

        // TODO: change needed
        def SONAR_TOKEN = "ASK_YOUR_INSTRUCTOR"
        def SONAR = "http://sonarqube-lab-infra." + APP_BASE
                
        def BUILD_NAME = APP_NAME
        def BUILD_IMAGE_STREAM = "openshift/redhat-openjdk18-openshift:1.4"
            
        def mvnCmd = "mvn -s ./nexus_openshift_settings.xml"

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
                        sh "${mvnCmd} clean package -DskipTests"
                    }
                }
            }
            
            stage('Test') {
                steps {
                    dir("${CONTEXT_DIR}") {
                        sh "${mvnCmd} test"
                    }
                }
            }
            
            stage('Sonar') {
                steps {
                    script {
                        dir("${CONTEXT_DIR}") {
                          sh "${mvnCmd} sonar:sonar -Dsonar.host.url=${SONAR} -Dsonar.projectName=${JOB_BASE_NAME} -Dsonar.login=${SONAR_TOKEN}"
                        }
                    }
                }
            }
            
            stage('Nexus') {
                steps {
                    script {
                      dir("${CONTEXT_DIR}") {
                        sh "${mvnCmd} deploy -DskipTests=true -DaltDeploymentRepository=nexus::default::${NEXUS}/repository/maven-snapshots/"
                        //APP_VERSION = sh (script: './gradlew -q getVersion --no-daemon', returnStdout: true).trim()
                        //sh "curl -v -u ${NEXUS_USERNAME}:${NEXUS_PASSWORD} --upload-file ./build/libs/${APP_NAME}-${APP_VERSION}.jar ${NEXUS}/repository/maven-snapshots/${NEXUS_PATH}/${APP_VERSION}/${APP_NAME}-${APP_VERSION}.jar"
                      }
                    }
                }
            }
                                
            stage('Create Image Builder') {
              when {
                expression {
                  openshift.withCluster() {
                    return !openshift.selector("bc", "${BUILD_NAME}").exists();
                  }
                }
              }
              steps {
                script {
                  openshift.withCluster() {
                    openshift.newBuild("--name=${BUILD_NAME}", "--image-stream=${BUILD_IMAGE_STREAM}", "--binary")
                  }
                }
              }
            }

            stage('Build Image') {
              steps {
                script {
                  dir("${CONTEXT_DIR}") {
                    openshift.withCluster() {
                        openshift.selector("bc", "${BUILD_NAME}").startBuild("--from-file=./target/${APP_NAME}-${APP_VERSION}.jar", "--wait")
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
                    openshift.tag("${BUILD_NAME}:latest", "${BUILD_NAME}:dev")
                  }
                }
              }
            }

            stage('Create DEV') {
              when {
                expression {
                  openshift.withCluster() {
                      openshift.withProject("${DEV_PROJECT_NAME}") {
                        return !openshift.selector('dc', "${APP_NAME}").exists()
                      }
                  }
                }
              }
              steps {
                script {
                  openshift.withCluster() {
                    openshift.withProject("${DEV_PROJECT_NAME}") {
                        openshift.newApp("${PROJECT_NAME}/${BUILD_NAME}:dev", "--name=${APP_NAME} -e DB_USERNAME=luke -e DB_PASSWORD=secret -e JAVA_OPTIONS=-Dspring.profiles.active=openshift").narrow('svc').expose()
                    }
                  }
                }
              }
            }
          }
        }
    type: JenkinsPipeline
~~~

Now it's time to start our pipeline, we can do this either from the CLI.

~~~shell
$ oc start-build bc/inventory-spring-boot-gradle-pipeline-complex -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
build "inventory-spring-boot-gradle-pipeline-complex-5" started
~~~

Or from the web-console, **Builds ➡ Pipelines**

![Pipeline Log]({% image_path inventory-spring-boot-gradle-cicd-complex-execution.png %}){:width="740px"}

After a successful pipeline built we should be able to run the following curl test with success.

~~~shell
$ oc get route -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
NAME        HOST/PORT                                                               PATH      SERVICES    PORT       TERMINATION   WILDCARD
inventory   inventory-{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev.apps.serverless-d50b.openshiftworkshop.com             inventory   8080-tcp                 None
$ curl http://inventory-{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev.apps.serverless-d50b.openshiftworkshop.com/api/inventory
~~~

Well done! You are ready to move on to the next lab.