## Alternative Inventory Service using Spring Boot + Maven

In this lab you will learn about **how you can build microservices using** **Spring Boot + Maven** and **Red Hat Openshift**.

#### Create projects to deploy our Inventory service

You can do this with the next commands or using the OpenShift web console.

~~~shell
oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
oc new-project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
~~~

Log into the Web Console if you haven't and go to `Advanced->Projects` and click on `Create Projects`.

![Prometheus]({% image_path inventory-spring-boot-deploy-create-projects-1.png %}){:width="740px"}

Use `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}` as project name and click on `Create`. Repeate the operation for `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev`.

![Prometheus]({% image_path inventory-spring-boot-deploy-create-projects-5.png %}){:width="740px"}

#### Let's deploy the databases in both projects

Click on `Topology`, be sure to select `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}`.

![Prometheus]({% image_path inventory-spring-boot-deploy-create-database-1.png %}){:width="740px"}

Click on `Database`.

![Prometheus]({% image_path inventory-spring-boot-deploy-create-database-2.png %}){:width="740px"}

Start typing `post`, then click PostgreSQL.

![Prometheus]({% image_path inventory-spring-boot-deploy-create-database-3.png %}){:width="740px"}

Click on `Instantiate Template`.

![Prometheus]({% image_path inventory-spring-boot-deploy-create-database-4.png %}){:width="740px"}

Fill the form with this data (leave the rest of fields by default):

* **Database Service Name:** my-database
* **PostgreSQL Connection Username:** luke
* **PostgreSQL Connection Password:** secret
* **PostgreSQL Database Name:** my_data

![Prometheus]({% image_path inventory-spring-boot-deploy-create-database-5.png %}){:width="740px"}

Change to project `{{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev` and repeat the operation. We need one database on each project.

#### Let's deploy the our code using the web console

![Prometheus]({% image_path inventory-spring-boot-deploy-code-1.png %}){:width="740px"}

![Prometheus]({% image_path inventory-spring-boot-deploy-code-2.png %}){:width="740px"}

![Prometheus]({% image_path inventory-spring-boot-deploy-code-3.png %}){:width="740px"}

![Prometheus]({% image_path inventory-spring-boot-deploy-code-4.png %}){:width="740px"}



#### Pipeline Example

~~~yaml
apiVersion: build.openshift.io/v1
kind: BuildConfig
metadata:
  name: inventory-spring-boot-maven-pipeline-complex
spec:
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfile: |-                
        def BUILD_NAME = APP_NAME
            
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
                        sh "${mvnCmd} clean package -DskipTests -Popenshift"
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
                          print "${mvnCmd} sonar:sonar -Dsonar.host.url=${SONAR} -Dsonar.projectName=${JOB_BASE_NAME} -Dsonar.login=${SONAR_TOKEN}"
                        }
                    }
                }
            }
            
            stage('Nexus') {
                steps {
                    script {
                      dir("${CONTEXT_DIR}") {
                        sh "${mvnCmd} deploy -DskipTests=true -DaltDeploymentRepository=nexus::default::${NEXUS}/repository/maven-snapshots/"
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
                        openshift.newApp("${DEV_PROJECT_NAME}/${BUILD_NAME}:latest", "--name=${APP_NAME} -e JAVA_OPTIONS=-Dspring.profiles.active=openshift").narrow('svc').expose()
                        def setEnvSecret = openshift.raw( "set env --from=secret/my-database dc/${APP_NAME}" )
                        echo "After set env from secret: ${setEnvSecret.out}"
                        //def setEnvConfig = openshift.raw( "set env --from=configmap/database-config dc/${APP_NAME}" )
                        //echo "After set env from config: ${setEnvConfig.out}"
                        //def liveness = openshift.raw( "set probe dc/${APP_NAME} --liveness --get-url=http://:8080/mpmo/health --initial-delay-seconds=180" )
                        //echo "After set probe liveness: ${liveness.out}"
                        //def readiness = openshift.raw( "set probe dc/${APP_NAME} --readiness --get-url=http://:8080/mpmo/health --initial-delay-seconds=10" )
                        //echo "After set probe readiness: ${readiness.out}"
                        def partOfLabel = openshift.raw( "label dc/${APP_NAME} app.kubernetes.io/part-of=${PART_OF}" )
                        echo "After label part-of partOfLabel: ${partOfLabel.out}"
                        def serviceLabel = openshift.raw( "label svc/${APP_NAME} team=spring-boot-actuator" )
                        echo "After label team serviceLabel: ${serviceLabel.out}"
                    }
                  }
                }
              }
            }

            stage('Approve') {
                steps {
                    timeout(time:15, unit:'MINUTES') {
                        input message:'Approve Deploy to TEST?'
                    }
                }
            }

            stage('Promote to TEST') {
              steps {
                script {
                  openshift.withCluster() {
                    openshift.tag("${BUILD_NAME}:latest", "${BUILD_NAME}:test")
                  }
                }
              }
            }

            stage('Create TEST') {
              when {
                expression {
                  openshift.withCluster() {
                      openshift.withProject("${TEST_PROJECT_NAME}") {
                        return !openshift.selector('dc', "${APP_NAME}").exists()
                      }
                  }
                }
              }
              steps {
                script {
                  openshift.withCluster() {
                    openshift.withProject("${TEST_PROJECT_NAME}") {
                        openshift.newApp("${DEV_PROJECT_NAME}/${BUILD_NAME}:test", "--name=${APP_NAME} -e DB_USERNAME=luke -e DB_PASSWORD=secret -e JAVA_OPTIONS=-Dspring.profiles.active=openshift").narrow('svc').expose()
                        //def setEnvSecret = openshift.raw( "set env --from=secret/my-database dc/${APP_NAME}" )
                        //echo "After set env from secret: ${setEnvSecret.out}"
                        //def setEnvConfig = openshift.raw( "set env --from=configmap/database-config dc/${APP_NAME}" )
                        //echo "After set env from config: ${setEnvConfig.out}"
                        //def liveness = openshift.raw( "set probe dc/${APP_NAME} --liveness --get-url=http://:8080/mpmo/health //--initial-delay-seconds=180" )
                        //echo "After set probe liveness: ${liveness.out}"
                        //def readiness = openshift.raw( "set probe dc/${APP_NAME} --readiness --get-url=http://:8080/mpmo/health //--initial-delay-seconds=10" )
                        //echo "After set probe readiness: ${readiness.out}"
                        def partOfLabel = openshift.raw( "label dc/${APP_NAME} app.kubernetes.io/part-of=${PART_OF}" )
                        echo "After label part-of partOfLabel: ${partOfLabel.out}"
                        def serviceLabel = openshift.raw( "label svc/${APP_NAME} team=spring-boot-actuator" )
                        echo "After label team serviceLabel: ${serviceLabel.out}"
                    }
                  }
                }
              }
            }
          }
        }
      env:
        - name: MAVEN_OPTS
          value: >-
            -Dsun.zip.disableMemoryMapping=true -Xms20m
            -Djava.security.egd=file:/dev/./urandom
            -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap
            -Dsun.zip.disableMemoryMapping=true
        - name: APP_BASE
          value: "apps.cluster-kharon-1f54.kharon-1f54.example.opentlc.com"
        - name: LAB_INFRA_PROJECT_NAME
          value: "lab-infra"
        - name: APP_NAME
          value: "inventory"
        - name: APP_VERSION
          value: "0.0.1-SNAPSHOT"
        - name: DEV_PROJECT_NAME
          value: "coolstore-1"
        - name: TEST_PROJECT_NAME
          value: "coolstore-1-dev"
        - name: GIT_URL
          value: "https://github.com/cvicens/inventory-api-1st-maven"
        - name: GIT_REF
          value: "solution"
        - name: CONTEXT_DIR
          value: "."
        - name: BUILD_IMAGE_STREAM
          value: "openshift/redhat-openjdk18-openshift:1.4"
        - name: NEXUS
          value: "http://nexus-lab-infra.apps.cluster-kharon-1f54.kharon-1f54.example.opentlc.com"
        - name: NEXUS_USERNAME
          value: "admin"
        - name: NEXUS_PASSWORD
          value: "admin123"
        - name: NEXUS_PATH
          value: "com/redhat/cloudnative/inventory"
        - name: SONAR_TOKEN
          value: "ASK_YOUR_INSTRUCTOR"
        - name: SONAR
          value: "ASK_YOUR_INSTRUCTOR"
        - name: JOB_BASE_NAME
          value: "inventory-service-1-job"
        - name: PART_OF
          value: "coolstore-app"
    type: JenkinsPipeline
~~~

Well done! You are ready to move on to the next lab.
