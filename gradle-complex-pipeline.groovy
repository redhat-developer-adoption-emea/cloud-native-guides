// In order for this pipeline to work PROJECT_NAME and DEV_PROJECT_NAME has to be created beforehand!

// oc new-project coolstore-dev

// oc policy add-role-to-user edit system:serviceaccount:coolstore:jenkins -n coolstore-dev
// oc policy add-role-to-user view system:serviceaccount:coolstore:jenkins -n coolstore-dev

//oc policy add-role-to-user system:image-puller system:serviceaccount:coolstore-dev:default -n coolstore

def APP_NAME = "inventory"
def APP_VERSION = "0.0.1-SNAPSHOT"

def PROJECT_NAME = "coolstore"
def DEV_PROJECT_NAME = PROJECT_NAME + "-dev"

def GIT_URL = "https://github.com/redhat-developer-adoption-emea/cloud-native-labs"
def GIT_REF = "ocp-3.10"
def CONTEXT_DIR = "inventory-spring-boot-gradle"

def NEXUS = "http://nexus-lab-infra.apps.istio.openshiftworkshop.com"
def NEXUS_USERNAME = "admin"
def NEXUS_PASSWORD = "admin123"
def NEXUS_PATH = "com/redhat/cloudnative/inventory"

def SONAR = "http://sonarqube-lab-infra.apps.istio.openshiftworkshop.com"
def SONAR_TOKEN = "f7d35ff77556cdf83fc0b78311201c4a2dd7227b"

def BUILD_NAME = APP_NAME
def BUILD_IMAGE_STREAM = "openjdk18-openshift:latest"
    
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
            //slackSend (color: '#ff0000', message: "Building")
            dir("${CONTEXT_DIR}") {
                sh "java -version"
                sh "./gradlew build --no-daemon -x test"
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
    
    stage('Sonar') {
        steps {
            script {
                dir("${CONTEXT_DIR}") {
                   sh "./gradlew sonarqube -Dsonar.host.url=${SONAR} -Dsonar.login=${SONAR_TOKEN} --no-daemon"
                }
            }
        }
    }
    
    stage('Nexus') {
        steps {
            script {
              dir("${CONTEXT_DIR}") {
                APP_VERSION = sh (script: './gradlew -q getVersion --no-daemon', returnStdout: true).trim()
                sh "curl -v -u ${NEXUS_USERNAME}:${NEXUS_PASSWORD} --upload-file ./build/libs/${APP_NAME}-${APP_VERSION}.jar ${NEXUS}/repository/maven-snapshots/${NEXUS_PATH}/${APP_VERSION}/${APP_NAME}-${APP_VERSION}.jar"
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
                openshift.selector("bc", "${BUILD_NAME}").startBuild("--from-file=./build/libs/${APP_NAME}-${APP_VERSION}.jar", "--wait")
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
