##  Managing Application Configuration

In this lab you will learn how to manage application configuration and how to provide environment 
specific configuration to the services.

#### Application Configuration

Applications require configuration in order to tweak the application behavior or adapt it to a certain environment without the need to write code and repackage the application for every change. These configurations are sometimes specific to the application itself such as the number of products to be displayed on a product page and some other times they are dependent on the environment they are deployed in such as the database coordinates for the application.

The most common way to provide configurations to applications is using environment variables and external configuration files such as properties, JSON or YAML files.

Configuration files and command line arguments should be externalized from the application and the docker image content in
order to keep the image portable across environments.

OpenShift provides a mechanism called [ConfigMaps]({{OPENSHIFT_DOCS_BASE}}/dev_guide/configmaps.html) in order to externalize configurations 
from the applications deployed within containers and provide them to the containers in a unified way as files and environment variables. OpenShift also offers a way to provide sensitive configuration data such as certificates, credentials, etc to the application containers in a secure and encrypted mechanism called Secrets.

This allows developers to build the container image for their application only once, and reuse that image to deploy the application across various environments with different configurations that are provided to the application at runtime.

#### Create PostgreSQL Databases for our Inventory Service

So far, the Inventory service have been using an in-memory H2 database. Although H2 is a convenient database to run locally on your laptop, it's in no way appropriate for production or even integration tests. Since it's strongly recommended to use the same technology stack (operating system, JVM, middleware, database, etc) that is used in production across all environments, you should modify the Inventory service to use PostgreSQL instead of the H2 in-memory database.

Fortunately, OpenShfit supports stateful applications such as databases which require access to a persistent storage that survives the container itself. You can deploy databases on OpenShift and regardless of what happens to the container itself, the data is safe and can be used by the next database container.

Let's create a [PostgreSQL database]({{OPENSHIFT_DOCS_BASE}}/using_images/db_images/postgresql.html) for the Inventory service using the PostgreSQL template that is provided out-of-the-box:

> [OpenShift Templates]({{OPENSHIFT_DOCS_BASE}}/dev_guide/templates.html) uses YAML/JSON to compose multiple containers and their configurations as a list of objects to be created and deployed at once hence making it simple to re-create complex deployments by just deploying a single template. Templates can be parameterized to get input for fields like service names and generate values for fields like passwords.

~~~shell
$ oc new-app postgresql-persistent \
    --param=DATABASE_SERVICE_NAME=inventory-postgresql \
    --param=POSTGRESQL_DATABASE=inventory \
    --param=POSTGRESQL_USER=inventory \
    --param=POSTGRESQL_PASSWORD=inventory \
    --labels=app=inventory
~~~

> The `--param` parameter provides a value for the template parameters. The recommended approach is not to provide any value for username and password and allow the template to generate a random value for you due to security reasons. In this lab in order to reduce typos, a fixed value is provided for username and password. The `--labels` allows assigning arbitrary key-value labels to the application objects in order to make it easier to find them later on when you have many applications in the same project.

Now you can move on to configure the Inventory service to use these PostgreSQL databases.

#### Externalize Spring Boot (Inventory) Configuration

Spring Boot application configuration is provided via a properties file called `application.properties` and can be [overriden and overlayed via multiple mechanisms](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html). 

> Check out the default Spring Boot configuration in the Inventory project `inventory-spring-boot-gradle/src/main/resources/application.properties`.

In this lab, you will configure the Inventory service which is based on Spring Boot to override the default configuration using an alternative `application.properties` backed by a config map.

Create a config map with the the Spring Boot configuration content using the PostgreSQL database credentials:

~~~shell
$ cat <<EOF > ./application.properties
spring.datasource.url=jdbc:postgresql://inventory-postgresql:5432/inventory
spring.datasource.username=inventory
spring.datasource.password=inventory
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=create
EOF
~~~

> The hostname defined for the PostgreSQL connection-url corresponds to the PostgreSQL service name published on OpenShift. This name will be resolved by the internal DNS server exposed by OpenShift and accessible to containers running on OpenShift.

~~~shell
$ oc create configmap inventory --from-file=./application.properties
~~~

> You can use the OpenShift Web Console to create config maps by clicking on **Resources >> Config Maps**  on the left sidebar inside the your project. Click on **Create Config Map** button to create a config map with the following info:
> 
> * Name: `inventory`
> * Key: `application.properties`
> * Value: *copy-paste the content of the above application.properties excluding the first and last lines (the lines that contain EOF)*

The [Spring Cloud Kubernetes](https://github.com/spring-cloud-incubator/spring-cloud-kubernetes) plug-in implements the integration between Kubernetes and Spring Boot and is already added as a dependency to the Inventory project. Using this dependency, Spring Boot would search for a config map (by default with the same name as the application) to use as the source of application configurations during application bootstrapping and if enabled, triggers hot reloading of beans or Spring context when changes are detected on the config map.

Although Spring Cloud Kubernetes tries to discover config maps, due to security reasons containers by default are not allowed to snoop around OpenShift clusters and discover objects. Security comes first, and discovery is a privilege that needs to be granted to containers in each project. 

Since you do want Spring Boot to discover the config maps inside the `{{COOLSTORE_PROJECT}}-{{OPENSHIFT_USER}}` project, you need to grant permission to the Spring Boot service account to access the OpenShift REST API and find the config maps.

~~~shell
$ oc policy add-role-to-user view -n {{COOLSTORE_PROJECT}}-{{OPENSHIFT_USER}} -z default
~~~

Delete the Inventory container to make it start again and look for the config maps:

~~~shell
$ oc delete pod -l app=inventory
~~~

When the Catalog container is ready, verify that the PostgreSQL database is being used. Check the Inventory pod logs:

~~~shell
$ oc logs dc/inventory | grep hibernate.dialect

2017-08-10 21:07:51.670  INFO 1 --- [           main] org.hibernate.dialect.Dialect            : HHH000400: Using dialect: org.hibernate.dialect.PostgreSQL94Dialect
~~~

You can also connect to the Inventory PostgreSQL database and verify that the seed data is loaded:

~~~shell
$ oc rsh dc/inventory-postgresql
~~~

Once connected to the PostgreSQL container, run the following:

> Run this command inside the Inventory PostgreSQL container, after opening a remote shell to it.

~~~shell
$ psql -U catalog -c "select * from inventory"
$ exit
~~~

#### Sensitive Configuration Data

Config map is a superb mechanism for externalizing application configuration while keeping containers independent of in which environment or on what container platform they are running. 
Nevertheless, due to their clear-text nature, they are not suitable for sensitive data like database credentials, SSH certificates, etc. In the current lab, we used config maps for database credentials to simplify the steps however for production environments, you should opt for a more secure way to handle sensitive data.

Fortunately, OpenShift already provides a secure mechanism for handling sensitive data which is called [Secrets]({{OPENSHIFT_DOCS_BASE}}/dev_guide/secrets.html). Secret objects act and are used similar to config maps however with the difference that they are encrypted as they travel over the wire and also at rest when kept on a persistent disk. Like config maps, secrets can be injected into 
containers as environment variables or files on the filesystem using a temporary file-storage facility (tmpfs).

You won't create any secrets in this lab however you have already created a secret when you created the PostgreSQL databases for the Catalog service. The PostgreSQL template by default stores the database credentials in a secret in the project it's being created:

~~~shell
$ oc describe secret inventory-postgresql

Name:            inventory-postgresql
Namespace:       coolstore-XX
Labels:          app=inventory
                 template=postgresql-persistent-template
Annotations:     openshift.io/generated-by=OpenShiftNewApp
                 template.openshift.io/expose-database_name={.data['database-name']}
                 template.openshift.io/expose-password={.data['database-password']}
                 template.openshift.io/expose-username={.data['database-user']}

Type:     Opaque

Data
====
database-name:        7 bytes
database-password:    7 bytes
database-user:        7 bytes
~~~

This secret has two encrypted properties defined as `database-user` and `database-password` which hold the PostgreSQL username and password values. These values are injected in the PostgreSQL container as environment variables and used to initialize the database.

Go to the **{{COOLSTORE_PROJECT}}-{{OPENSHIFT_USER}}** in the OpenShift Web Console and click on the `catalog-postgresql` deployment (blue text under the title **Deployment**) and then on the **Environment**. Notice the values from the secret are defined as env vars on the deployment:

![Secrets as Env Vars]({% image_path config-psql-secret.png %}){:width="900px"}

That's all for this lab! You are ready to move on to the next lab.