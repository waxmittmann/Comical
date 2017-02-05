# Setup
First, in the 'conf' folder copy or move 'example.api.properties' to 'api.properties'
and add a valid private and public key.

Then start the server with ```sbt "run"```. By default, the server will come up
at localhost:9000.

# Making requests
Hit the comics endpoint (/comics) with GET requests with a url-encoded comicIds parameter
set to an array of comma-separated integer ids, e.g:
```localhost:9000/comics?comicIds=2323%2C1%2C2%2C3%2C4```

# Response format
## For successful requests, a status code 200 with a json body of the following format:
```
{
    "data": array of comics data contained in JsObjects,
    notFound: JsArray of integers of comic ids that returned 404 responses,
    badJsonSchema: JsArray of integers of comic ids that returned json missing the data.result element (this should not happen)
    malformedJson: JsArray of integers of comic ids that returned invalid json (this should not happen)
    success: boolean that is 'true' if there were no badJsonSchema or malformedJson results, false otherwise
}
```
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
Include a url-encoded comicIds parameter whose value is a list of comma-separated comic ids

### When the parameter cannot be parsed to a list of integers
comicIds ($comicIdQueryString) could not be parsed to an array of ints: $err

### When there are too many ids (the default maximum per request is 50)
Too many items, please include at most $maxQueriesPerRequest ids

## For requests for which there is a backend failure in remote requests to the marvel api, a 500 status and:
There was an error handling your request. Please try again.






Things to talk about:
- why url-encoded params rather than json for requests
- why play
- why no (few) functional tests

Things to worry about:
- UTF8 (especially with my byte string deserializer)
