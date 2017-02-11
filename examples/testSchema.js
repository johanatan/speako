
var speako = require('../');
var gql = require('graphql');

var schema = speako.getSchema(new Object, process.argv[2]);
console.log("Loaded ", schema);

