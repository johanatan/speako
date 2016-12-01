
var starWarsData = require('./starWarsData.js');
var speako = require('../');
var gql = require('graphql');

var getters = {"Human": starWarsData.getHuman, "Droid": starWarsData.getDroid};
var dataResolver = {"query":  function (typename, predicate) {
  var res = getters[typename].call(null, parseInt(predicate.split("=")[1]));
  return res;
}};
var starWarsSchema = speako.getSchema(dataResolver, "./schema.gql");

gql.graphql(starWarsSchema, "{ Human(id: 1000) { name }}").then(function (res) {
  console.log(res);
});
