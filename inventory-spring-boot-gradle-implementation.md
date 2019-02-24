## Coding an implementation for our Inventory API

Before we start adding actual code, we have to take some coutermeasures to avoid those changes being overwritten if we re-generate code from the API specification.

Open file `.openapi-generator-ignore` find the the following lines right.

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
application*.properties
...
~~~

#### Adding database related dependencies
Open `build.gradle` and add the next dependencies.

~~~java
...
dependencies {
	...
	// <<< Prometheus Metrics (1)

	// >>> Database related (1)
	implementation('org.springframework.boot:spring-boot-starter-data-jpa')
	runtimeOnly('com.h2database:h2')
	runtimeOnly('org.postgresql:postgresql')
	// <<< Database related (1)
}
...
~~~

#### Creating a Spring Data CRUD Repository
Spring Data is a very powerful CRUD framework that basically is made of:

* **CrudRepository<Entity, Integer>** → `InventoryRepository`
* **Entity** → `InventoryItemImpl`

The way we're going to override the default (example) implementation is modifying the mocked `InventoryApiController` so that it uses the **CrudRepository**.

In order to decouple the **Controller** from the **API Implementation** we'll need an additional class → `InventoryApiImpl`.

Let's get started. Please create a package called `com.redhat.cloudnative.inventory.impl`.

Now create a file `InventoryApiImpl` with this content, this class is annotated as `@Service` exposes the two methods we need:

* **inventoryGet** → returns all items
* **inventoryItemIdGet** → returns only one item

~~~java
package com.redhat.cloudnative.inventory.impl;

import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class InventoryApiImpl {
    @Bean
    InventoryApiImpl getInventoryApiImpl() {
        return new InventoryApiImpl();
    }

    public List<InventoryItemImpl> inventoryGet(InventoryRepository repository) {
        Spliterator<InventoryItemImpl> items = repository.findAll()
        .spliterator();

        return StreamSupport
                .stream(items, false)
                .collect(Collectors.toList());
    }

    public InventoryItemImpl inventoryItemIdGet(String itemId, InventoryRepository repository) {
        List<InventoryItemImpl> items = repository.findByItemId(itemId);

        return items.size() > 0 ? items.get(0) : null;
    }
   
}
~~~

We also need the implementation of the Spring Data Crud Repository, please create a file called `InventoryRepository` in the same package with the following content.

~~~java
package com.redhat.cloudnative.inventory.impl;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface InventoryRepository extends CrudRepository<InventoryItemImpl, Integer> {
    public List<InventoryItemImpl> findAll();
    public List<InventoryItemImpl> findByItemId(String itemId);
}
~~~

As you can see the repository is based on an entity called `InventoryItemImpl`, so let's create an Entity object that resambles the InventoryItem Data Type part of the API specification.

~~~java
package com.redhat.cloudnative.inventory.impl;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;

@Entity
@Table(name = "INVENTORY", uniqueConstraints = @UniqueConstraint(columnNames = "itemId"))
public class InventoryItemImpl implements Serializable {
	private static final long serialVersionUID = -8053933344541613739L;

	@Id
    private String itemId;

    private int quantity;

    public InventoryItemImpl() {
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "Inventory [" +
                "itemId='" + itemId + '\'' +
                ", quantity=" + quantity +
                ']';
    }
}
~~~

Now let's update the Controller (class that implemets the Inventory API) so that it uses our brand new implementation.

> As you can see there are a couple of dependencies injected: **inventoryRepository**, **inventoryApiImpl**
> 
> We have also overriden **inventoryItemIdGet** and **inventoryGet** with the new implementations. As you can check we also need a mapper between the API version of the InventoryItem and the version we use to persist to the Data Base. We'll create this class later.

~~~java
package com.redhat.cloudnative.inventory.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;

import io.micrometer.core.instrument.Metrics;

import java.util.List;
import java.util.Optional;

import com.redhat.cloudnative.inventory.impl.DataMapper;
import com.redhat.cloudnative.inventory.impl.InventoryApiImpl;
import com.redhat.cloudnative.inventory.impl.InventoryRepository;
import com.redhat.cloudnative.inventory.model.InventoryItem;
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2019-02-24T17:36:36.165+01:00[Europe/Madrid]")

@Controller
@RequestMapping("${openapi.inventory.base-path:/api}")
public class InventoryApiController implements InventoryApi {

    private final NativeWebRequest request;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryApiImpl inventoryApiImpl;

    @Autowired
    public InventoryApiController(NativeWebRequest request) {
        this.request = request;
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

    @Override
    public ResponseEntity<InventoryItem> inventoryItemIdGet(String itemId) {
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory/" + itemId).increment();

        InventoryItem item = DataMapper.toInventoryItem(inventoryApiImpl.inventoryItemIdGet(itemId, inventoryRepository));

        if (item == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);    
        }

        return new ResponseEntity<>(item, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<InventoryItem>> inventoryGet() {
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", "/inventory").increment();
        
        List<InventoryItem> items = DataMapper.toInventoryItemList(inventoryApiImpl.inventoryGet(inventoryRepository));
        return new ResponseEntity<>(items, HttpStatus.OK);
    }
}
~~~

Finally let's create the `DataMapper` class.

~~~java
package com.redhat.cloudnative.inventory.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.redhat.cloudnative.inventory.model.InventoryItem;

public class DataMapper {
    public static InventoryItem toInventoryItem (InventoryItemImpl from) {
        if (from == null) {
            return null;
        }

        InventoryItem to = new InventoryItem();
        to.setItemId(from.getItemId());
        to.setQuantity(from.getQuantity());

        return to;
    }

    public static List<InventoryItem> toInventoryItemList (List<InventoryItemImpl> from) {
        return from.stream()
                   .map(item -> toInventoryItem(item))
                   .collect(Collectors.toList());
    }
}
~~~

#### Adding sample data to the database
Create a new file named `import.sql` in `./src/main/resources` with the next content.

> **Spring Boot Data will execute this file for us**

~~~sql
INSERT INTO INVENTORY(item_id, quantity) VALUES (329299, 35)
INSERT INTO INVENTORY(item_id, quantity) VALUES (329199, 12)
INSERT INTO INVENTORY(item_id, quantity) VALUES (165613, 45)
INSERT INTO INVENTORY(item_id, quantity) VALUES (165614, 87)
INSERT INTO INVENTORY(item_id, quantity) VALUES (165954, 43)
INSERT INTO INVENTORY(item_id, quantity) VALUES (444434, 32)
INSERT INTO INVENTORY(item_id, quantity) VALUES (444435, 53)
~~~ 

Well done! You are ready to move on to the next lab.
