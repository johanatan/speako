# speako

### A simpler interface to GraphQL

#### Install

* NPM - `npm install speako`

#### Motivation

GraphQL normally requires a `GraphQLSchema` object passed along with each query
you give it to validate, interpret & execute. Typically this schema is constructed
by hand-crafting some verbose & noisy JavaScript.

See: [starWarsSchema.js](https://github.com/graphql/graphql-js/blob/master/src/__tests__/starWarsSchema.js).

The equivalent schema in GraphQL Schema Language is much more concise:
```
enum Episode { NEWHOPE, EMPIRE, JEDI }

type Human {
  id: ID!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  homePlanet: String
}

type Droid {
  id: ID!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  primaryFunction: String
}

union Character = Human | Droid
```

Given a specification of a data model in GraphQL Schema Language, speako automatically
generates the `GraphQLSchema` instance that GraphQL requires and binds its `resolve` methods
to a specified set of functions for querying (i.e., selecting) and mutating (i.e., insert,
update and delete mutations).

#### Example

```javascript
$ node --harmony-destructuring
> var speako = require('speako');
> var gql = require('graphql');
> var _ = require('lodash');
> var labels = [
... {'id': 1, 'name': 'Apple Records', 'founded': '1968'},
... {'id': 2, 'name': 'Harvest Records', 'founded': '1969'}];
> var albums = [
... {'id': 1, 'name': 'Dark Side Of The Moon', 'releaseDate': 'March 1, 1973',
...  'artist': 'Pink Floyd', 'label': labels[1]},
... {'id': 2, 'name': 'The Beatles', 'releaseDate': 'November 22, 1968',
...  'artist': 'The Beatles', 'label': labels[0]},
... {'id': 3, 'name': 'The Wall', 'releaseDate': 'August 1, 1982',
...  'artist': 'Pink Floyd', 'label': labels[1]}];
> var dataResolver = {"query":  function (typename, predicate) {
...   console.assert(typename == "Album");
...   var parsed = JSON.parse(predicate);
...   if (_.isEqual(parsed, {"all": true})) return albums;
...   else {
...     var pairs = _.toPairs(parsed);
...     var filters = pairs.map(function (p) {
...       var [field, value] = p;
...       if (typeof value === "object") {
...         console.assert(field == "label");
...         return function(elem) {
...           var innerKey = _.first(_.keys(value));
...           return elem[field][innerKey] == value[innerKey]; };
...       }
...       return function(elem) { return elem[field] == value; };
...     });
...     return albums.filter(function(elem) { return filters.every(function(f) { return f(elem); }); });
...   }
... }, "create": function (typename, inputs) {
...   inputs.id = albums.length + 1;
...   albums.push(inputs);
...   return inputs;
... }};
> var schema = speako.getSchema(dataResolver,
... "type Album { id: ID! name: String releaseDate: String artist: String }");
> var schema =
... speako.getSchema(dataResolver,
...               ["type Label { id: ID! name: String founded: String album: Album } ",
...                "type Album { id: ID! name: String releaseDate: String artist: String label: Label }"].join(" "));
> var printer = function(res) { console.log(JSON.stringify(res, null, 2)); };
> gql.graphql(schema,
...  "{ Album(artist: \"Pink Floyd\", label: { name: \"Harvest Records\" }) { name artist releaseDate } }") .then(printer);

{
  "data": {
    "Album": [
      {
        "name": "Dark Side Of The Moon",
        "artist": "Pink Floyd",
        "releaseDate": "March 1, 1973"
      },
      {
        "name": "The Wall",
        "artist": "Pink Floyd",
        "releaseDate": "August 1, 1982"
      }
    ]
  }
}

> gql.graphql(schema, "{ Album(artist: \"Pink Floyd\") { name artist releaseDate } }").then(printer);

{
  "data": {
    "Album": [
      {
        "name": "Dark Side Of The Moon",
        "artist": "Pink Floyd",
        "releaseDate": "March 1, 1973"
      },
      {
        "name": "The Wall",
        "artist": "Pink Floyd",
        "releaseDate": "August 1, 1982"
      }
    ]
  }
}

> gql.graphql(schema, "{ Album(artist: \"Pink Floyd\", name: \"The Wall\") { name artist releaseDate } }").then(printer);

{
  "data": {
    "Album": [
      {
        "name": "The Wall",
        "artist": "Pink Floyd",
        "releaseDate": "August 1, 1982"
      }
    ]
  }
}

> gql.graphql(schema, "{ Album(id: 2) { name artist releaseDate } }").then(printer);

{
  "data": {
    "Album": [
      {
        "name": "The Beatles",
        "artist": "The Beatles",
        "releaseDate": "November 22, 1968"
      }
    ]
  }
}

> gql.graphql(schema, "{ Albums { name artist releaseDate } }").then(printer);

{
  "data": {
    "Albums": [
      {
        "name": "Dark Side Of The Moon",
        "artist": "Pink Floyd",
        "releaseDate": "March 1, 1973"
      },
      {
        "name": "The Beatles",
        "artist": "The Beatles",
        "releaseDate": "November 22, 1968"
      },
      {
        "name": "The Wall",
        "artist": "Pink Floyd",
        "releaseDate": "Auguest 1, 1982"
      }
    ]
  }
}

> gql.graphql(schema, "mutation m { createAlbum(name:\"The Division Bell\", releaseDate: \"March 28, 1994\", artist:\"Pink Floyd\") { id name } }").then(printer);

{
  "data": {
    "createAlbum": {
      "id": "4",
      "name": "The Division Bell"
    }
  }
}

```

Copyright (c) 2015 Jonathan L. Leonard
