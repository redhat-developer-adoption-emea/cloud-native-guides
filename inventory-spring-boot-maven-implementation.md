## Coding an implementation for our Inventory API

Before we start adding actual code, we have to take some coutermeasures to avoid those changes being overwritten if we re-generate code from the API specification.

Open file `.openapi-generator-ignore` find the following lines.

~~~shell
# Use this file to prevent files from being overwritten by the generator.
# The patterns follow closely to .gitignore or .dockerignore.
~~~

And update accordingly to match the following.

~~~shell
...
# Use this file to prevent files from being overwritten by the generator.
# The patterns follow closely to .gitignore or .dockerignore.
*Impl.java
InventoryApiController.java
InventoryItem.java
OpenAPIDocumentationConfig.java
application*.properties
...
~~~

#### Fabric8 Plugin configuration

We're going to use `fabric8` to deploy to Openshift and this means we have to make some changes to our `pom.xml` file and some additional files.

Open `pom.xml` and add the next properties in the `properties` section.

> Look for `<!-- fabric8 s2i image -->` and add the section.

> If Java 8

~~~xml
...
<properties>
   <java.version>1.8</java.version>
   <maven.compiler.source>${java.version}</maven.compiler.source>
   <maven.compiler.target>${java.version}</maven.compiler.target>
   <springfox-version>2.8.0</springfox-version>
   <!-- fabric8 s2i image -->
   <openjdk18-openshift.version>latest</openjdk18-openshift.version>
    <fabric8.generator.from>registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift:${openjdk18-openshift.version}</fabric8.generator.from>
    <fabric8-maven-plugin.version>4.1.0</fabric8-maven-plugin.version>
</properties>
...
~~~

> If Java 11

~~~xml
...
<properties>
   <java.version>1.8</java.version>
   <maven.compiler.source>${java.version}</maven.compiler.source>
   <maven.compiler.target>${java.version}</maven.compiler.target>
   <springfox-version>2.8.0</springfox-version>
   <!-- fabric8 s2i image -->
   <openjdk-11-openshift.version>latest</openjdk-11-openshift.version>
   <fabric8.generator.from>registry.access.redhat.com/openjdk/openjdk-11-rhel7:${openjdk-11-openshift.version}</fabric8.generator.from>
   <fabric8-maven-plugin.version>4.1.0</fabric8-maven-plugin.version>
</properties>
...
~~~

#### Adding database related dependencies

Open `pom.xml` and add the next dependencies.

> We're going to need JDBC drivers for both H2 and PostgreSQL

~~~xml
    ...
    <!-- DB -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>runtime</scope>
    </dependency>
    
     <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
</project>
~~~

Additionally let's add two profiles to our `pom.xml` so that we can use H2 embedded database locally and PostgreSQL when running in Openshift.

> **NOTE:** Add the next profiles section after the `dependencies` section

~~~xml
	...
     </dependencies>
     <profiles>
        <profile>
        <id>local</id>
        <activation />
        <dependencies>
            <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
            </dependency>
        </dependencies>
        <properties>
            <db.name>H2</db.name>
        </properties>
        </profile>
        <profile>
        <id>openshift</id>
        <dependencies>
            <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
            </dependency>
        </dependencies>
        <activation />
        <properties>
            <db.name>PostgreSQL</db.name>
        </properties>
        <build>
            <plugins>
            <plugin>
                <groupId>io.fabric8</groupId>
                <artifactId>fabric8-maven-plugin</artifactId>
                <executions>
                <execution>
                    <id>fmp</id>
                    <goals>
                    <goal>resource</goal>
                    <goal>build</goal>
                    </goals>
                </execution>
                </executions>
            </plugin>
            </plugins>
        </build>
        </profile>
        <profile>
        <id>openshift-it</id>
        <build>
            <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                <systemPropertyVariables>
                    <app.name>${project.artifactId}</app.name>
                </systemPropertyVariables>
                <classesDirectory>${project.build.directory}/${project.build.finalName}.${project.packaging}.original</classesDirectory>
                </configuration>
                <executions>
                <execution>
                    <goals>
                    <goal>integration-test</goal>
                    <goal>verify</goal>
                    </goals>
                </execution>
                </executions>
            </plugin>
            </plugins>
        </build>
        <activation />
        </profile>
    </profiles>
</project>    
~~~

Run `mvn spring-boot:run` to check that we haven't broken anything.

#### Adapt application properties to

Please open file `./src/main/resources/application.properties ` and add the following properties.

~~~shell
...
# Data Source
spring.jpa.hibernate.ddl-auto=create
spring.datasource.initialization-mode=always
~~~

#### Creating the Spring Data Repository

Create a folder `data` under `./src/main/java/com/redhat/cloudnative/inventory` then create a file named `InventoryRepository.java` with the next content:

~~~java
package com.redhat.cloudnative.inventory.data;

import com.redhat.cloudnative.inventory.model.InventoryItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

public interface InventoryRepository extends JpaRepository<InventoryItem, Integer> {
    public InventoryItem findByItemId(String itemId);
}

~~~

#### Adjusting the InventoryItem for Spring Data

Now we have modify our `InventoryItem` so that it can be used from `InventoryRepository`.

> These are the changes needed:
> * Add `@Entity(name = "Inventory")` to `InventoryItem`
> * Add `@Id` to `private String itemId`
> * Adding imports for `javax.persistence.Entity` and import `javax.persistence.Id`

Look at these changes in the Java class (only relevant code shown).

~~~java
...
import javax.persistence.Entity;
import javax.persistence.Id;
...
@Entity(name = "Inventory")
public class InventoryItem  implements Serializable  {
  @Id
  @JsonProperty("itemId")
  private String itemId;
  ...
~~~

#### Overriding the default Inventory API

So far we have been returning default 'sample' data, as we have seen previously in file `./src/main/java/com/redhat/cloudnative/inventory/api/InventoryApi.java`. Nevertheless, if you look closely, InventoryAPI is an `interface`, not a `class`, which defines `default` implementations for the methods we're interested in: `inventoryItemIdGet` and `inventoryGet`.

The real implementation, though, should be planted in `class` `InventoryApiController.java`. 

> **Path:** `./src/main/java/com/redhat/cloudnative/inventory/api/InventoryApiController.java`

Original java code:

> As you can see there are no implementations for methods `inventoryItemIdGet` and `inventoryGet` so the default ones in `InventoryApi` will be used.

~~~java
package com.redhat.cloudnative.inventory.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;
import java.util.Optional;
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2019-10-14T19:25:58.590+03:00[Asia/Qatar]")

@Controller
@RequestMapping("${openapi.inventory.base-path:/api}")
public class InventoryApiController implements InventoryApi {

    private final NativeWebRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryApiController(NativeWebRequest request) {
        this.request = request;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

}
~~~

Here you are the most interesting changes introduced:

> *Only relevant parts of the code are hightlighted below*

**Injection of an InventoryRepository bean**

~~~java
@org.springframework.beans.factory.annotation.Autowired
private InventoryRepository inventoryRepository;
~~~

**Using InventoyRepository to get all InventoryItems**

~~~java
public ResponseEntity<List<InventoryItem>> inventoryGet() {
    ...
    List<InventoryItem> _items = inventoryRepository.findAll();
    ...
}
~~~

**Using InventoyRepository to get an InventoryItem by Id**

~~~java
@Override
public ResponseEntity<InventoryItem> inventoryItemIdGet(@PathVariable("itemId") String itemId) {
    ...
    InventoryItem _item = inventoryRepository.findByItemId(itemId);
    if (_item != null) {
        item.setItemId(_item.getItemId());
        item.setQuantity(_item.getQuantity());
    }
    ...
}
~~~

Please substitute the current implementation of `InventoryApiController` with the next one.

~~~java
package com.redhat.cloudnative.inventory.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;

import io.micrometer.core.instrument.Metrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import com.redhat.cloudnative.inventory.data.InventoryRepository;
import com.redhat.cloudnative.inventory.model.InventoryItem;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2019-04-23T11:23:32.594+02:00[Europe/Madrid]")

@Controller
@RequestMapping("${openapi.inventory.base-path:/api}")
public class InventoryApiController implements InventoryApi {

    private final NativeWebRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    private InventoryRepository inventoryRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public InventoryApiController(NativeWebRequest request) {
        this.request = request;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

    @Override
    public ResponseEntity<List<InventoryItem>> inventoryGet() {
        // >>> Prometheus metric
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory").increment();
        // <<< Prometheus metric

        List<InventoryItem> items = new ArrayList<InventoryItem>();

        try {
            getRequest().ifPresent(request -> {
                for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                    if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                        HttpServletResponse res = request.getNativeResponse(HttpServletResponse.class);
                        res.setCharacterEncoding("UTF-8");
                        res.addHeader("Content-Type", "application/json");
                        
                        List<InventoryItem> _items = inventoryRepository.findAll();
                        items.addAll(_items);
                        break;
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return new ResponseEntity<List<InventoryItem>>(items, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<InventoryItem> inventoryItemIdGet(@PathVariable("itemId") String itemId) {
        // >>> Prometheus metric
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory/" + itemId).increment();
        // <<< Prometheus metric

        InventoryItem item = new InventoryItem();
        try {
            getRequest().ifPresent(request -> {
                for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                    if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                        HttpServletResponse response = request.getNativeResponse(HttpServletResponse.class);
                        response.setCharacterEncoding("UTF-8");
                        response.addHeader("Content-Type", "application/json");
                        
                        InventoryItem _item = inventoryRepository.findByItemId(itemId);
                        if (_item != null) {
                            item.setItemId(_item.getItemId());
                            item.setQuantity(_item.getQuantity());
                        }
                        break;
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        if (item.getItemId() == null) {
            return new ResponseEntity<InventoryItem>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<InventoryItem>(item, HttpStatus.OK);
    }

}
~~~


> **NOTE:** You'll find that we haven't forgotten to add the code to update the Prometheus counters we created in previous labs. You could delete the Prometheus related lines of code in `InventoryAPI` or just leave it as is.

#### Adding sample data to the database

One of the properties (in the `application.properties` file), `spring.datasource.schema`, refers to a file `import.sql`, as you can imagine it's there to help us adding some sample data to H2. So let's create it in the same folder as the properties files.

~~~shell
$ cat <<EOF > ./src/main/resources/import.sql
INSERT INTO INVENTORY(ITEM_ID, QUANTITY) VALUES ('329299', 35);
INSERT INTO INVENTORY(ITEM_ID, QUANTITY) VALUES ('329199', 12);
INSERT INTO INVENTORY(ITEM_ID, QUANTITY) VALUES ('165613', 45);
INSERT INTO INVENTORY(ITEM_ID, QUANTITY) VALUES ('165614', 87);
INSERT INTO INVENTORY(ITEM_ID, QUANTITY) VALUES ('165954', 43);
INSERT INTO INVENTORY(ITEM_ID, QUANTITY) VALUES ('444434', 32);
INSERT INTO INVENTORY(ITEM_ID, QUANTITY) VALUES ('444435', 53);
EOF
~~~

#### Testing our implementation

As usual, let's run `mvn spring-boot:run` and let's check that everything works as expected, for instance using curl (although I encourage you to also use the web ui as we did before).

~~~shell
$ curl http://localhost:8080/api/inventory
[{"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},{"itemId":"165954","quantity":43},{"itemId":"329199","quantity":12},{"itemId":"329299","quantity":35},{"itemId":"444434","quantity":32},{"itemId":"444435","quantity":53}]
~~~

#### Deploying our new version

This time, our code needs a database, a real one -PostgreSQL-, not H2. So the first we're going to do is deploy it in both enviroments: 

* {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
* {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev

Let's deploy our databases.

~~~shell
$ oc new-app -e POSTGRESQL_USER=luke -ePOSTGRESQL_PASSWORD=secret -ePOSTGRESQL_DATABASE=my_data openshift/postgresql-92-centos7 --name=my-database -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
$ oc new-app -e POSTGRESQL_USER=luke -ePOSTGRESQL_PASSWORD=secret -ePOSTGRESQL_DATABASE=my_data openshift/postgresql-92-centos7 --name=my-database -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
~~~

We're going to use `fabric8` to deploy to Openshift and this means we have to make some changes to our `pom.xml` file and some additional files.

> Maybe you have noticed that we added the plugin before as part of the 'openshift' profile (and along with the PostgreSQL driver).

Open `pom.xml` and add the next properties in the `properties` section.

> `<fabric8.generator.from>openshift/java:8</fabric8.generator.from>`

~~~xml
...
<properties>
   <java.version>1.8</java.version>
   <maven.compiler.source>${java.version}</maven.compiler.source>
   <maven.compiler.target>${java.version}</maven.compiler.target>
   <springfox-version>2.8.0</springfox-version>
   <!-- fabric8 s2i image -->
   <fabric8.generator.from>openshift/java:8</fabric8.generator.from>
</properties>
...
~~~

Fabric8 is the plugin we use to deploy our code and if we don't do nothing it will use a *by default* `Deployment`, nevertheless our deployment needs some enviroment variables (and probes) to work properly, namely:

* **DB_USERNAME:** which should be `luke`
* **DB_PASSWORD:** which should be `secret`
* **JAVA_OPTIONS:** which should select the `openshift` profile we created before

> **NOTICE:** we need to define probes because the actuator url `/actuator/health` is not the default `/health`

The way we tell `fabric8` to customize the `Deployment` and/or add other kubernetes objects is put them in folder `./src/main/fabric8`. Execute the following commands:

> Make sure you're at `inventory-spring-boot-maven/inventory-gen`. If in doubt review chapter **Generating the code**!

~~~shell
$ mkdir ./src/main/fabric8
$ cat <<EOF > ./src/main/fabric8/deployment.yml
apiVersion: v1
kind: Deployment
metadata:
  name: ${project.artifactId}
spec:
  template:
    spec:
      containers:
        - env:
            - name: DB_USERNAME
              value: luke
            - name: DB_PASSWORD
              value: secret
            - name: JAVA_OPTIONS
              value: "-Dspring.profiles.active=openshift"
          livenessProbe:
            failureThreshold: 3
            httpGet:
              path: /actuator/health
              port: 8080
              scheme: HTTP
            initialDelaySeconds: 30
            periodSeconds: 10
            successThreshold: 1
            timeoutSeconds: 1
          readinessProbe:
            failureThreshold: 3
            httpGet:
              path: /actuator/health
              port: 8080
              scheme: HTTP
            initialDelaySeconds: 10
            periodSeconds: 10
            successThreshold: 1
            timeoutSeconds: 1
EOF
~~~

As we have mentioned a couple of times, we need the deployment to use the `openshift` profile when running in Openshift... and this profile should include the information to set up the DataSource acoordingly... So what's left is creating the properties file for our profile.

> **Pay attention to the # PostgreSQL section**, all the other data is as it was before and so it's not repeated in this new properties file
> 
> **NOTICE:** When non-embedded DB `intialization-mode=always` is needed if you want your schema script to be run

~~~shell
cat <<EOF > ./src/main/resources/application-openshift.properties
# PostgreSQL
spring.datasource.initialization-mode=always
spring.datasource.url=jdbc:postgresql://${MY_DATABASE_SERVICE_HOST}:${MY_DATABASE_SERVICE_PORT}/my_data
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
EOF
~~~

#### Deploying our service using fabric8

First of let's be sure we're at the right project.

> **NOTICE:** For the `fabric8` plugin to work you need to be logged in and in the correct project.

~~~shell
$ oc project {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
$ mvn fabric8:deploy -Popenshift
~~~

Well done! You are ready to move on to the next lab.
