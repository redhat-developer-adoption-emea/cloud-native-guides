## Using APICUR.IO to design the inventory API

In this lab you will use [API Curio](https://www.apicur.io/) to design the API for the Inventory Service.

#### Designing our API

In order to design our API we're going to use [API Curio](https://www.apicur.io/), an online API designer supporting Open API 3.x and Swagger (Open API 2.x)

Please go to [API Curio](https://www.apicur.io/) and login (you can user your github/google account to login)

![apicur.io Login]({% image_path dotnet-apicurio-login.png %}){:width="740px"}

After a successful login you should land in the dashboard area.

![apicur.io Login]({% image_path dotnet-apicurio-dashboard-area.png %}){:width="740px"}

Please, click on APIs (left side), there you should be able to see your APIs, none if it's the first time you use it. Click on `Create New API` to start designing our Inventory API.

![apicur.io Login]({% image_path dotnet-apicurio-apis-area.png %}){:width="740px"}

Now give to your API `Name`, `Description`, select version `Open API 3.0.2` and finally select `Blank API` as the starting point, as in the next picture.

![apicur.io New API]({% image_path dotnet-apicurio-new-api.png %}){:width="740px"}

At this point we have an empty API, so let's start editing the API by clicking on `Edit API`.

![apicur.io Edit API]({% image_path dotnet-apicurio-edit-api.png %}){:width="740px"}

The first thing we're going to do is to create the InventoryItem type, so click on `Add a data Type` or the plus sign next to `Data Types`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-datatype.png %}){:width="740px"}

In order to create our data type we have to provide a Name a Description and a sample JSON object (although optional we're are going to use this feature to make the editing somewhat easier). After entering the following data please click on `Save`.

>
* **Name:** `InventoryItem`
* **Description:** `The root of the InventoryItem type's schema.`
* **Sample JSON object:**
> 
~~~json
{"itemId":"329299","quantity":35}
~~~

![apicur.io Edit API]({% image_path dotnet-apicurio-new-datatype-edit.png %}){:width="740px"}

Now that we have our InventoryItem data type, it's time to define the API. Defining the API consist on defining `paths` like:

* **/api/inventory** to get all the items in the inventory
* **/api/inventory/{itemId}** to get a specific item

Let's create our first path, click on `Add a Path`

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall.png %}){:width="740px"}

Please type `/api/inventory` and click on `Add`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-01.png %}){:width="740px"}

As you can see there are no operations defined yet. As the Inventory API should return all the items with a simple `GET` with no parameters. To do so let's define a `GET` operation by selecting `GET` first and clicking on `Add Operation` afterwards.

> **GET** should be selected (blue underlining) by default, if that's not the case, please make sure it is.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-02.png %}){:width="740px"}

Let's edit the description and type something like: **"Should return all elements as an array of InventoryItems or an empty array if there are none."**

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-03.png %}){:width="740px"}

Now if you scroll down a little, you'll see a warning sign underneath the `Responses` area. Click on `Add a response` to add an HTTP 200 response.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-04.png %}){:width="740px"}

Click on `Add` (200 should be selected by default).

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-05.png %}){:width="740px"}

Add a description to the response, for instance: **"Should return an arry of InventoryItems"**.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-06.png %}){:width="740px"}

Click on `No response media types defined` and then on `Add media type`

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-07.png %}){:width="740px"}

Now, choose `application/json`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-08.png %}){:width="740px"}

The answer should be an `Array` (of JSON objects) of type `InventoryItem`. 

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-09.png %}){:width="740px"}

Once you have chosen the proper type, please click on `Add Example`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-10.png %}){:width="740px"}

Copy and paste the following example and click on `Add`.

~~~json
[{"itemId":"329299","quantity":35},{"itemId":"329199","quantity":12},
{"itemId":"165613","quantity":45},{"itemId":"165614","quantity":87},
{"itemId":"165954","quantity":43},{"itemId":"444434","quantity":32},
{"itemId":"444435","quantity":53}]
~~~

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getall-11.png %}){:width="740px"}

That's all needed related to the first path, let's do the same for the second. Remember click on the plus sign.

![apicur.io New Path]({% image_path dotnet-apicurio-new-path-getone-00.png %}){:width="400px"}

This time we need a path parameter to search for a specific, like this one: `/api/inventory/{itemId}`

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-01.png %}){:width="740px"}

Following up with the path parameter, scroll down to `Path Parameters` you'll find that there is one already created (inferred from the path you typed in before). Click on `Create` to actually create it.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-02.png %}){:width="740px"}

Then set the type of the parameter to `String`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-03.png %}){:width="740px"}

We're all set with regards to parameters, now it's time to create an operation, again is a `GET` operation. So click on the `Add operation` button (make sure `GET` is selected).

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-04.png %}){:width="740px"}

Again, let's add a description to the action, something like: **Returns the item for the id provided or an error**

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-05.png %}){:width="740px"}

Once again we have a warning in the `Responses` area we have to take care of. Please click on `Add Response`

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-06.png %}){:width="740px"}

Choose `200` (default) and click `Add`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-07.png %}){:width="740px"}

Let's add a description, something like this: **Should return the item for the id provided**.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-08.png %}){:width="740px"}

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-09.png %}){:width="740px"}

As we did before, we have to add a media type `application/json`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-10.png %}){:width="740px"}

Don't forget to click on `Add`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-11.png %}){:width="740px"}

This operation should return an inventory item, so please choose `InventoryItem` as the response type.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-12.png %}){:width="740px"}

As previously, let's add an example, named `OneItem`

~~~ json
{"itemId":"329299","quantity":35}
~~~

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-13.png %}){:width="740px"}

We have to add another response for the case when we cannot find the item id provided. But before we do, we're going to add a new type to encode the error as a JSON object. Click on the plus sign to create a new Data Type.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-datatype-small.png %}){:width="400px"}

Create a new data type with the following values.

>
* **Name:** `GenericError`
* **Description:** `Generic Error Object.`
* **Sample JSON object:**
> 
~~~json
{
   "code" : "404",
   "message" : "Item 53 was not found"
}
~~~

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-14.png %}){:width="740px"}

Now let's add a new response. This time the Status Code needs to be `404`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-15.png %}){:width="740px"}

Add a description as in the next pictures.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-16.png %}){:width="740px"}

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-17.png %}){:width="740px"}

And as usual a new media type of type `application/json`.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-18.png %}){:width="740px"}

This time we have to choose `GenericError` as the response type.

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-19.png %}){:width="740px"}

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-20.png %}){:width="740px"}

Finally let's add a sample JSON object named `NotFoundError`.

~~~json
{
   "code" : "404",
   "message" : "Item 53 was not found"
}
~~~

![apicur.io Edit API]({% image_path dotnet-apicurio-new-path-getone-21.png %}){:width="740px"}



Well done! You are ready to move on to the next lab.
