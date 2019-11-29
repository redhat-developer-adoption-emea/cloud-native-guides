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
InventoryApi.java
OpenAPIDocumentationConfig.java
application*.properties
...
~~~

#### Fabric8 Plugin configuration

We're going to use `fabric8` to deploy to Openshift and this means we have to make some changes to our `pom.xml` file and some additional files.

Open `pom.xml` and add the next properties in the `properties` section.

> Look for `<!-- fabric8 s2i image -->` and add the section.

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
    <!-- MyBatis -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>2.0.1</version>
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

#### Adapt application properties to MyBatis

Please open file `./src/main/resources/application.properties ` and add the following properties.

~~~shell
...
# MyBatis
spring.datasource.schema=classpath:import.sql
logging.level.root=WARN
logging.level.sample.mybatis.mapper=TRACE
~~~

#### Creating the InventoryMapper

Let's create a My Batis mapper to implement the Inventory Service methods: `inventoryItemIdGet` and `inventoryGet`.

~~~shell
cat <<EOF > ./src/main/java/com/redhat/cloudnative/inventory/mapper/InventoryMapper.java
package com.redhat.cloudnative.inventory.mapper;

import java.util.List;
import java.util.Optional;

import com.redhat.cloudnative.inventory.model.InventoryItem;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryMapper {

	@Select("select itemId, quantity from inventory")
	List<InventoryItem> findAll();

	@Select("select itemId, quantity from inventory where itemId = #{itemId}")
	Optional<InventoryItem> findByItemId(@Param("itemId") Integer itemId);

}
~~~

#### Overriding the default Inventory API

So far we have been returning default 'sample' data, as we have seen previously in file `./src/main/java/com/redhat/cloudnative/inventory/api/InventoryApi.java`. Nevertheless, if you look closely, InventoryAPI is an `interface`, not a `class`, which defines `default` implementations for the methods we're interested in: `inventoryItemIdGet` and `inventoryGet`.

The real implementation, though, should be planted in `class`, `InventoryApiController.java`. Please substitute the current implementation with the next one.

> Pay attention to the implementation of the methods, specially where we use the InventoryMapper as in: `List<InventoryItem> _items = inventoryMapper.findAll();`
> 
> **Path:** `./src/main/java/com/redhat/cloudnative/inventory/api/InventoryApi.java`

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

import com.redhat.cloudnative.inventory.mapper.InventoryMapper;
import com.redhat.cloudnative.inventory.model.InventoryItem;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2019-04-23T11:23:32.594+02:00[Europe/Madrid]")

@Controller
@RequestMapping("${openapi.inventory.base-path:/api}")
public class InventoryApiController implements InventoryApi {

    private final NativeWebRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    private final InventoryMapper inventoryMapper = null;

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
                        
                        List<InventoryItem> _items = inventoryMapper.findAll();
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
                        
                        inventoryMapper.findByItemId(new Integer(itemId)).ifPresent(_item -> {
                            item.setItemId(_item.getItemId());
                            item.setQuantity(_item.getQuantity());
                        });
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

> As you can see there is  a dependency injected with `@AutoWired`: **inventoryMapper**.
> 
> We have also overriden **inventoryItemIdGet** and **inventoryGet** with the new implementations.
> 
> You'll find that we haven't forgotten to add the code to update the Prometheus counters we created in previous labs. 

#### Adding sample data to the database

One of the properties (in the `application.properties` file), `spring.datasource.schema`, refers to a file `import.sql`, as you can imagine it's there to help us adding some sample data to H2. So let's create it in the same folder as the properties files.

~~~shell
$ cat <<EOF > ./src/main/resources/import.sql
drop table if exists INVENTORY;
create table INVENTORY (itemId int primary key, quantity int);

INSERT INTO INVENTORY(itemId, quantity) VALUES (329299, 35);
INSERT INTO INVENTORY(itemId, quantity) VALUES (329199, 12);
INSERT INTO INVENTORY(itemId, quantity) VALUES (165613, 45);
INSERT INTO INVENTORY(itemId, quantity) VALUES (165614, 87);
INSERT INTO INVENTORY(itemId, quantity) VALUES (165954, 43);
INSERT INTO INVENTORY(itemId, quantity) VALUES (444434, 32);
INSERT INTO INVENTORY(itemId, quantity) VALUES (444435, 53);
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
oc new-app -e POSTGRESQL_USER=luke -ePOSTGRESQL_PASSWORD=secret -ePOSTGRESQL_DATABASE=my_data centos/postgresql-10-centos7 --name=my-database -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
oc label dc/my-database app.kubernetes.io/part-of=fruit-service-app -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}
oc new-app -e POSTGRESQL_USER=luke -ePOSTGRESQL_PASSWORD=secret -ePOSTGRESQL_DATABASE=my_data centos/postgresql-10-centos7 --name=my-database -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
oc label dc/my-database app.kubernetes.io/part-of=fruit-service-app -n {{COOLSTORE_PROJECT}}{{PROJECT_SUFFIX}}-dev
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

As we have mentioned a couple of time, we need the deployment to use the `openshift` profile when running in Openshift... and this profile should include the information to set up the DataSource acoordingly... So what's left is creating the properties file for our profile.

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
