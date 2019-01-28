def GUID = "serverless-3bf7"

def APP_NAME = "inventory-dotnet-core-complex"
def APP_VERSION = "0.0.1-SNAPSHOT"

def PROJECT_NAME = "coolstore-01"
def DEV_PROJECT_NAME = "coolstore-dev-01"

def GIT_URL = "https://github.com/redhat-developer-adoption-emea/cloud-native-labs"
def GIT_REF = "ocp-3.10"
def CONTEXT_DIR = "solutions/lab-2-dotnet-core/inventory-dotnet-core"

def BUILD_NAME = APP_NAME
def BUILD_IMAGE_STREAM = "dotnet:2.1"

def DOTNET_STARTUP_PROJECT = "src/Org.OpenAPITools/Org.OpenAPITools.csproj"
def DOTNET_FRAMEWORK = "netcoreapp2.1"
def DOTNET_CONFIGURATION = "Release"
def DOTNET_APP_PATH = "/tmp/publish"

def DOTNET_PROJECT_KEY = "Org.OpenAPITools"
def DOTNET_PROJECT_NAME = "Org.OpenAPITools"
def DOTNET_PROJECT_VERSION = "1.0"

def SONAR_HOST = "http://sonarqube-lab-infra.apps.${GUID}.openshiftworkshop.com"
def SONAR_TOKEN = "50cf600faefa2fe5fb09eaef5ce7691363e2d6b9"

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
                sh "dotnet restore \"${DOTNET_STARTUP_PROJECT}\""
                sh "dotnet publish \"${DOTNET_STARTUP_PROJECT}\" -f \"${DOTNET_FRAMEWORK}\" -c \"${DOTNET_CONFIGURATION}\"  --self-contained false /p:PublishWithAspNetCoreTargetManifest=false --no-restore -o \"${DOTNET_APP_PATH}\""
            }
        }
    }
    
    stage('Test') {
        steps {
            dir("${CONTEXT_DIR}") {
                sh "echo TODO dotnet test..."
            }
        }
    }
    
    stage('Sonar') {
        steps {
            script {
                dir("${CONTEXT_DIR}") {
                    // https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+MSBuild
                    sh "mono /opt/sonar-scanner-msbuild/SonarScanner.MSBuild.exe begin /d:sonar.host.url=$SONAR_HOST /d:sonar.login=$SONAR_TOKEN /k:$DOTNET_PROJECT_KEY /n:\"$DOTNET_PROJECT_NAME\" /v:$DOTNET_PROJECT_VERSION"
                    sh "dotnet restore \"${DOTNET_STARTUP_PROJECT}\""
                    sh "dotnet build \"${DOTNET_STARTUP_PROJECT}\""
                    sh "mono /opt/sonar-scanner-msbuild/SonarScanner.MSBuild.exe end /d:sonar.login=$SONAR_TOKEN"
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
