// Don't forget to run the commands to create the dev project, and grant the needed roles to the service accounts

def APP_BASE = "apps.aramco-6a76.openshiftworkshop.com"

def APP_NAME = "karma-tests"
def APP_VERSION = "0.0.1-SNAPSHOT"

def PROJECT_NAME = "coolstore"
def DEV_PROJECT_NAME = PROJECT_NAME + "-dev"

def GIT_URL = "https://github.com/redhat-developer-adoption-emea/cloud-native-labs.git"
def GIT_REF = "ocp-3.10"
def CONTEXT_DIR = "karma-tests"

def NEXUS = "http://nexus-lab-infra." + APP_BASE
def NEXUS_USERNAME = "admin"
def NEXUS_PASSWORD = "admin123"
def NEXUS_PATH = "com/redhat/cloudnative/inventory"

def SONAR = "http://sonarqube-lab-infra." + APP_BASE
def SONAR_TOKEN = "2866ddfc53ef43c23a3c7e3fd37903bf7329bfb0"

def BUILD_NAME = APP_NAME
def BUILD_IMAGE_STREAM = "openshift/nodejs:8"
    
pipeline {
  agent {
    label 'karma'
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
                sh "export NPM_CONFIG_PREFIX=~/.npm-global"
                sh "npm install"
                sh "npx ng build"
                sh "tar cvf ./dist/${APP_NAME}-${APP_VERSION}.tar -C ./dist/karma-tests/ ."
            }
        }
    }
    
    stage('Test') {
        steps {
            dir("${CONTEXT_DIR}") {
                sh "npx ng test"
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
            openshift.newBuild("${BUILD_IMAGE_STREAM}~${GIT_URL}#${GIT_REF}", "--name=${BUILD_NAME}", "--context-dir=${CONTEXT_DIR}")
          }
        }
      }
    }

    stage('Build Image') {
      steps {
        script {
          dir("${CONTEXT_DIR}") {
            openshift.withCluster() {
                openshift.selector("bc", "${BUILD_NAME}").startBuild("--wait")
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