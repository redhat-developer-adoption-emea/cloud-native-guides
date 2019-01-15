// In order for this pipeline to work PROJECT_NAME and DEV_PROJECT_NAME has to be created beforehand!

// oc new-project coolstore-dev

// oc policy add-role-to-user edit system:serviceaccount:coolstore:jenkins -n coolstore-dev
// oc policy add-role-to-user view system:serviceaccount:coolstore:jenkins -n coolstore-dev
// oc policy add-role-to-user system:image-puller system:serviceaccount:coolstore-dev:default -n coolstore

// oc policy add-role-to-user edit system:serviceaccount:{{COOLSTORE_PROJECT}}:jenkins -n {{COOLSTORE_PROJECT}}-dev
// oc policy add-role-to-user view system:serviceaccount:{{COOLSTORE_PROJECT}}:jenkins -n {{COOLSTORE_PROJECT}}-dev
// oc policy add-role-to-user system:image-puller system:serviceaccount:{{COOLSTORE_PROJECT}}-dev:default -n {{COOLSTORE_PROJECT}}

def GUID = "serverless-3bf7"

def APP_NAME = "inventory-dotnet-core"
def APP_VERSION = "0.0.1-SNAPSHOT"

def PROJECT_NAME = "coolstore-01"
def DEV_PROJECT_NAME = PROJECT_NAME + "-dev-01"

def GIT_URL = "https://github.com/redhat-developer-adoption-emea/cloud-native-labs"
def GIT_REF = "ocp-3.10"
def CONTEXT_DIR = "solutions/lab-2-dotnet-core/inventory-dotnet-core"

def DOTNET_STARTUP_PROJECT = "src/Org.OpenAPITools"
def DOTNET_FRAMEWORK = "netcoreapp2.1"
def DOTNET_CONFIGURATION = "Release"
def VERBOSITY_OPTION = 5
def DOTNET_APP_PATH = "/tmp/build"

def NEXUS = "http://nexus-lab-infra.apps.${GUID}.openshiftworkshop.com"
def NEXUS_USERNAME = "admin"
def NEXUS_PASSWORD = "admin123"
def NEXUS_PATH = "com/redhat/cloudnative/inventory"

def SONAR = "http://sonarqube-lab-infra.apps.${GUID}.openshiftworkshop.com"
def SONAR_TOKEN = "f7d35ff77556cdf83fc0b78311201c4a2dd7227b"

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
