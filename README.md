# Setup
First, in the 'conf' folder in application.conf, find the blank 
*comical.marvel.publickey* and *comical.marvel.privatekey* entries and enter 
valid marvel api keys.

Then start the server with 
```sbt "run"```. 
By default, the server will come up at localhost:9000. Hitting '/' or '/comics'
with a GET should produce a text message.

# Making requests
Hit the comics endpoint (/comics) with GET requests with a url-encoded comicIds parameter
set to an array of comma-separated integer ids, e.g:
```
localhost:9000/comics?comicIds=2323%2C1%2C2%2C3%2C4
```

# Response format
## For successful requests, a status code 200 with a json body of the following attributes:
- data: array of comics data contained in JsObjects,
- notFound: JsArray of integers of comic ids that returned 404 responses,
- badJsonSchema: JsArray of integers of comic ids that returned json missing the data.result element (this should not happen)
- malformedJson: JsArray of integers of comic ids that returned invalid json (this should not happen)
- success: boolean that is 'true' if there were no badJsonSchema or malformedJson results, false otherwise

Example:
```
{
    data: [
        {
            id: 2323,
            digitalId: 1538,
            title: "Black Panther (2005) #7",
            issueNumber: 7,
            ...
        },
        ... more JsObjects ...
    ],
    success: true,
    notFound: [1],
    badJsonSchema: [ ],
    malformedJson: [ ]
}
```

## For requests with a missing or invalid comicIds parameter, a 400 status and:
### When the parameter is missing
```
Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids
```

### When the parameter cannot be parsed to a list of integers
```
comicIds ($comicIdQueryString) could not be parsed to an array of ints: $err
```

### When there are too many ids (the default maximum per request is 50)
```
Too many items, please include at most $maxQueriesPerRequest ids
```

## For requests for which there is a backend failure in remote requests to the marvel api, a 500 status and:
```
There was an error handling your request. Please try again.
```


# Design decisions
## Play
I started out with Scalatra because I was looking for a lightweight solution. 
When both making http requests and returning asynchronous results required a bunch 
of extra plumbing, I decided to go with the kitchen sink (Play) instead. I generally
prefer kitchen-sink frameworks for MVP-type applications to reduce setup cost.

## Why not parse the json from the marvel api into an internal data structure?
We're actually just passing through that data. 

## Why no end-to-end / proper functional tests?
I decided to use time elsewhere as the application is simple enough so that I'm 
not too worried about everything working together, since I'm not dev-ing this 
as something that's fully production-ready. Those tests should definitely be written
before the app gets to production though.

## Why an url-encoded param instead of POST or json requests
It's nice and simple and sufficient for our usecase.

# Todos / Extensions
## UTF8
There might be problems with UTF8, I'm dealing with everything as plain strings
