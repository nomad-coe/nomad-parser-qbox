(function() {
'use strict';

var dataModule = angular.module('metaDataApp.dataModule', []);

dataModule.factory('dataService', function($http) {
    var promiseVersionList;
    var promiseMetaInfoList;
    var promiseAllParents;
    var currentVersion = 'common';
    var myService = {
        getVersion: function(){
        return currentVersion;
    },
    setVersion: function(ver){
        currentVersion = ver;
        return currentVersion;
    },
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
        // $http returns a promise, which has a then function, which also returns a promise
        promiseMetaInfoList = $http.get('/nmi/v/'+version+'/annotatedinfo.json',{cache: true}).then(function (response) {
          //cache is set to true; the data should be fetched only once for each version;  
          //http://stackoverflow.com/questions/16286605/angularjs-initialize-service-with-asynchronous-data
          // The then function here is an opportunity to modify the response
          // The return value gets picked up by the then in the controller.
        return response.data;
        });
        // Return the promise to the controller
        return promiseMetaInfoList;
    },
    asyncAllParents: function(version,metaName) {
        // $http returns a promise, which has a then function, which also returns a promise
        promiseAllParents = $http.get('/nmi/v/'+version+'/n/' + metaName+'/allparents.json',{cache: true}).then(function (response) {
        //cache is set to true; the data should be fetched only once for each version;
        //http://stackoverflow.com/questions/16286605/angularjs-initialize-service-with-asynchronous-data
        // The then function here is an opportunity to modify the response
        // The return value gets picked up by the then in the controller.
        return response.data;
        });
        // Return the promise to the controller
        return promiseAllParents;
    }
  };
  return myService;
});

})();