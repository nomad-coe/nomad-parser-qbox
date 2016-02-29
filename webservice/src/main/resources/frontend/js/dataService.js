(function() {
'use strict';

var dataModule = angular.module('metaDataApp.dataModule', []);

dataModule.factory('dataService', function($http) {
    var promiseVersionList;
    var promiseMetaInfoList;
    var promiseMetaAllParentGraph;
    var promiseMetaInfoListGraph;
    //Add more versions here as they are available
    var mappedNames = function(){
        var types = {
            'type_dimension' : 'Dimension',
            'type_section': 'Section',
            'type_document_content': 'Concrete Value',
            'type_abstract_document_content':'Abstract Type'
        }
        var versions = {
            "all": "all",
            "last": "last",
            "common": "common only"
        }
        return {
            'types': types,
            'versions': versions,
            'type': function(t) {
                var res = types[t]
                if (res === undefined)
                    return t
                else
                    return res
            },
            'version': function(t) {
                var res = versions[t]
                if (res === undefined)
                    return "common + " + t
                else
                    return res
            }
        }
    }();
    var myService = {
    asyncVersionList: function() {
        // $http returns a promise, which has a then function, which also returns a promise
        promiseVersionList = $http.get('/nmi/info.json',{cache: true}).then(function (response) {
          //cache is set to true; the data should be fetched only once for each version;  
          //http://stackoverflow.com/questions/16286605/angularjs-initialize-service-with-asynchronous-data
          // The then function here is an opportunity to modify the response
          // The return value gets picked up by the then in the controller.
          return response.data;
        });
        // Return the promise to the controller
        return promiseVersionList;
    },
    asyncMetaDataList: function(version) {
        promiseMetaInfoList = $http.get('/nmi/v/'+version+'/annotatedinfo.json',{cache: true}).then(function (response) {
            return response.data;
        });
        return promiseMetaInfoList;
    },
    asyncMetaInfoAncestorChildrenGraph: function(version,metaName) {
        promiseMetaAllParentGraph = $http.get('/nmi/v/'+version+'/n/' + metaName+'/metainfograph.json',{cache: true}).then(function (response) {
            return response.data;
        });
        return promiseMetaAllParentGraph;
    },
    asyncMetaInfoListGraph: function(version,metaNames) {
        promiseMetaInfoListGraph = $http({
                                     url: '/nmi/v/'+version+'/multiplemetainfograph.json',
                                     method: "GET",
                                     params: {"metaInfoList":metaNames.toString()}
                                   }).then(function (response) {
            return response.data;
        });
        return promiseMetaInfoListGraph;
    },
    mappedNames: mappedNames
  };
  return myService;
});

})();

