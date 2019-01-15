## Creating a complex .Net Core based Jenkins pipeline

This lab is a spin-off of s set of labs regarding CI/CD, you can find the original version [here](https://github.com/openshift-labs/devops-guides).

Here we're going to use a custom Jenkins slave to build NodeJS + Angular 6 application running a pipeline which will run karma tests on a custom image where we have installed Chrome and NG.


#### Building a complex pipe-line leveraging our custom Jenkins slave

Now we're going to create an OpenShift Pipeline that embeds a pipeline definition that builds our app using `dotnet`, test it, builds an image, deploy the app and promote the image to our dev environment `{{COOLSTORE_PROJECT}}-dev`.

> The next pipeline (or to be precise Jenkins' service account in project `{{COOLSTORE_PROJECT}}`) needs to be able to `edit` and `view` contents in project `{{COOLSTORE_PROJECT}}-dev`. 
> Additionally the default service account in project `{{COOLSTORE_PROJECT}}-dev` needs to be able to pull an image from an image stream in project `{{COOLSTORE_PROJECT}}`, this means we have to add this role `system:image-puller` to this service account `system:serviceaccount:coolstore-dev:default`

Please run this commands to fulfill the requisites referred to above.

~~~ shell
$ export MY_USER_NUMBER="XX"
$ oc policy add-role-to-user edit system:serviceaccount:coolstore-${MY_USER_NUMBER}:jenkins -n coolstore-dev-${MY_USER_NUMBER}
$ oc policy add-role-to-user view system:serviceaccount:coolstore-${MY_USER_NUMBER}:jenkins -n coolstore-dev-${MY_USER_NUMBER}
$ oc policy add-role-to-user system:image-puller system:serviceaccount:coolstore-dev-${MY_USER_NUMBER}:default -n coolstore-${MY_USER_NUMBER}
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

> * **`coolstore`** should be **`coolstore-XX`**
> * **`coolstore-dev`** should be **`coolstore-dev-XX`**

~~~shell
$ cat << EOF | oc create -n "coolstore-${MY_USER_NUMBER}" -f -
apiVersion: v1
kind: BuildConfig
metadata:
  name: karma-pipeline-complex
spec:
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-
        def APP_BASE = "${APP_BASE}"

        def APP_NAME = "inventory-dotnet-core"
        def APP_VERSION = "0.0.1-SNAPSHOT"

        def PROJECT_NAME = "coolstore-${MY_USER_NUMBER}"
        def DEV_PROJECT_NAME = "coolstore-dev-${MY_USER_NUMBER}"

        def GIT_URL = "${GIT_URL}"
        def GIT_REF = "${GIT_REF}"
        def CONTEXT_DIR = "solutions/lab-2-dotnet-core/inventory-dotnet-core"

        def DOTNET_STARTUP_PROJECT = "src/Org.OpenAPITools"
        def DOTNET_FRAMEWORK = "netcoreapp2.1"
        def DOTNET_CONFIGURATION = "Release"
        def VERBOSITY_OPTION = 5
        def DOTNET_APP_PATH = "/tmp/build"

        def NEXUS = "http://nexus-lab-infra." + APP_BASE
        def NEXUS_USERNAME = "admin"
        def NEXUS_PASSWORD = "admin123"
        def NEXUS_PATH = "com/redhat/cloudnative/inventory"

        def SONAR = "http://sonarqube-lab-infra." + APP_BASE
        def SONAR_TOKEN = "${SONAR_TOKEN}"

        def BUILD_NAME = APP_NAME
        def BUILD_IMAGE_STREAM = "dotnet:2.1"

        pipeline {
          agent {
            label 'dotnet'
          }
          stages {
            stage('Checkout') {
              steps {
                git url: "${GIT_URL}", branch: "${GIT_REF}"
              }
            }
            
            stage('Build') {
                steps {
                    //slackSend (color: '#ff0000', message: "Building")
                    dir("${CONTEXT_DIR}") {
                        sh "dotnet restore \"${DOTNET_STARTUP_PROJECT}\" ${RESTORE_OPTIONS} ${VERBOSITY_OPTION}"
                        sh "dotnet publish \"${DOTNET_STARTUP_PROJECT}\" -f \"${DOTNET_FRAMEWORK}\" -c \"${DOTNET_CONFIGURATION}\" ${VERBOSITY_OPTION} --self-contained false /p:PublishWithAspNetCoreTargetManifest=false --no-restore -o \"${DOTNET_APP_PATH}\""
                    }
                }
            }
            
            stage('Test') {
                steps {
                    dir("${CONTEXT_DIR}") {
                        sh "echo dotnet test..."
                    }
                }
            }
            
            stage('Sonar') {
                steps {
                    script {
                        dir("${CONTEXT_DIR}") {
                          // https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+MSBuild
                          // dotnet tool install --global dotnet-sonarscanner
                          // dotnet sonarscanner begin /k:"project-key"
                          // dotnet build <path to solution.sln>
                          // dotnet sonarscanner end
                          sh "echo dotnet sonarqube plugin..."
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
                        openshift.selector("bc", "${BUILD_NAME}").startBuild("--from-dir=${DOTNET_APP_PATH}", "--wait")
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
                        openshift.newApp("${PROJECT_NAME}/${BUILD_NAME}:dev", "--name=${APP_NAME}").narrow('svc').expose()
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

![Pipeline Log]({% image_path devops-start-build-karma-tests-pipeline.png %}){:width="740px"}

After a successful pipeline built we should be able to visit our NodeJS application. Please go to {{COOLSTORE_PROJECT}}-dev project and have a look to the Overview area, it should look like this. 

![Pipeline Log]({% image_path devops-karma-tests-overview.png %}){:width="740px"}

Once there, please click on the link.

![Pipeline Log]({% image_path devops-karma-tests-app-deployed.png %}){:width="740px"}
