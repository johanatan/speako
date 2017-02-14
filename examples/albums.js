
var speako = require('../');
var gql = require('graphql');
var _ = require('lodash');
var labels = [
    {'id': 1, 'name': 'Apple Records', 'founded': '1968'},
    {'id': 2, 'name': 'Harvest Records', 'founded': '1969'}];
var albums = [
    {'id': 1, 'name': 'Dark Side Of The Moon', 'releaseDate': 'March 1, 1973',
     'artist': 'Pink Floyd', 'label': labels[1]},
    {'id': 2, 'name': 'The Beatles', 'releaseDate': 'November 22, 1968',
     'artist': 'The Beatles', 'label': labels[0]},
    {'id': 3, 'name': 'The Wall', 'releaseDate': 'August 1, 1982',
     'artist': 'Pink Floyd', 'label': labels[1]}];
var getFilters = function(pairs) {
  return pairs.map(function (p) {
    var [field, value] = p;
    if (typeof value === "object") {
      console.assert(field == "label");
      return function(elem) {
        var innerKey = _.first(_.keys(value));
        return elem[field][innerKey] == value[innerKey]; };
    }
    return function(elem) { return elem[field] == value; };
  });
};
var dataResolver = {"query":  function (typename, predicate) {
  console.assert(typename == "Album");
  var parsed = JSON.parse(predicate);
  if (_.isEqual(parsed, {"all": true})) return albums;
  else {
    var pairs = _.toPairs(parsed);
    var filters = getFilters(pairs);
    return albums.filter(function(elem) { return filters.every(function(f) { return f(elem); }); });
  }
}, "create": function (typename, inputs) {
  inputs.id = albums.length + 1;
  albums.push(inputs);
  return inputs;
}, "delete": function (typename, inputs) {
  console.assert(typename == "Album");
  var filters = getFilters(_.toPairs(inputs));
  var [deleted, remaining] = _.partition(albums, function(elem) { return filters.every(function(f) { return f(elem); }); });
  albums = remaining;
  return _.head(deleted);
}};
var schema =
  speako.getSchema(dataResolver,
                ["type Label { id: ID! name: String founded: String album: Album } ",
                 "type Album { id: ID! name: String releaseDate: String artist: String label: Label }"].join(" "));
var printer = function(res) { console.log(JSON.stringify(res, null, 2)); };
speako.setDebug(true);
gql.graphql(schema,
  "{ Album(artist: \"Pink Floyd\", label: { name: \"Harvest Records\" }) { name artist releaseDate } }") .then(printer);
gql.graphql(schema, "{ Album(artist: \"Pink Floyd\", name: \"The Wall\") { name artist releaseDate } }").then(printer);
gql.graphql(schema, "{ Album(id: 2) { name artist releaseDate } }").then(printer);
gql.graphql(schema, "{ Albums { name artist releaseDate } }").then(printer);
gql.graphql(schema,
  "mutation m { createAlbum(name:\"The Division Bell\", releaseDate: \"March 28, 1994\", artist:\"Pink Floyd\") { id name } }")
    .then(printer);
gql.graphql(schema, "mutation m { deleteAlbum(id: 3) { id name } }").then(printer);
gql.graphql(schema, "mutation m { deleteAlbum(name: \"The Beatles\") { id releaseDate } }").then(printer);
