(function() {
    'use strict';
    // to set a module
    angular.module('metaDataApp.mainController', ['ngSanitize', 'ui.select','angular.filter','metaDataApp.metaFilter','metaDataApp.dataModule','mathJaxDirective'])
    .controller('mainController', ['$scope', '$http','$location', 'ancestorGraph', '$routeParams','filterService','dataService',mainController])
    .factory('ancestorGraph', ['$q','$location','$rootScope', ancestorGraphDeclaration])
    .directive('master',masterDirective)
    .filter('filterAllQueries', queriesFilter);

    function mainController($scope, $http, $location, ancestorGraph, $routeParams,filterService,dataService){
        $scope.metaInfoName = $routeParams.metaInfoName;
        $scope.version = $routeParams.version;
        $scope.filter = filterService.filter;
        $scope.mappedNames = dataService.mappedNames;
        $scope.dataToDisplay = {
            name:'',
            description:''
        };
        $scope.showOnlyDerived = false;
        $scope.cyLoaded = false;
        //For UI select is needs to be an array in a object.
        $scope.combinedGraph = {selectedMetaInfos:[]}
        var cy;
        $scope.DAG ={
            //zoom: Store the zoom and "pan" setting of the graph
            zoom:false,
            zoomText:'Enable zoom'
        }
        dataService.asyncVersionList().then(function(versions) {
            $scope.versions =  angular.fromJson(versions)['versions'];
            $scope.versions.sort();
            //Check if requested version exists; otherwise redirect to common
            if($scope.versions.indexOf($scope.version) == -1){
                $scope.version = 'common';
                $location.path('/'+$scope.version+'/'+$scope.metaInfoName);
            }
        });

        //Get version specific data
        dataService.asyncMetaDataList($scope.version).then(function(metaData) {
            $scope.metaDataList = angular.fromJson(metaData);
            $scope.display();
        });

        $scope.set_color = function (derived) {
          if (derived) {
            return { color: "red" }
          }
        }


        $scope.display = function(){
            var i = -1;
            for (i = 0; i < $scope.metaDataList['metaInfos'].length ; i++) {
                if($scope.metaDataList['metaInfos'][i]['name'] == $scope.metaInfoName){
                    $scope.dataToDisplay = $scope.metaDataList['metaInfos'][i];
                    break;
                }
            }
            //If metaData not found then redirect to section_single_configuration_calculation
            if (i == $scope.metaDataList['metaInfos'].length ){
                $location.path('/'+$scope.version+'/section_single_configuration_calculation');
            }
            else{
                dataService.asyncAllParentsCS($scope.version,$scope.metaInfoName ).then(function(allparentsData) {
                    ancestorGraph( allparentsData ).then(function( ancestorCy ){
                        cy = ancestorCy;
                        $scope.cyLoaded = true;
//                        graphOptions(allparentsData);
                        drawChildrenAsList(allparentsData);
                    });
                });
            }
        }

        var drawChildren = function(allparentsData) {
        var cMinX = 200,
            cMinY = 100,
            currCol = 0,
            currRow = 0,
            maxRow = 7; //max rows in a coloumn

        if( allparentsData.children.length > 0 ) {
            var ele = ancestorGraph.getElementById("Children of "+$scope.metaInfoName)
            var eleX = ele.position().x,
                eleY = ele.position().y;
            var childX = 0,
                childY = 0;
            if(allparentsData.children.length < maxRow){
                maxRow =  allparentsData.children.length;
            }
            for (var i = 0; i < allparentsData.children.length; i++)
            {
                if(currRow >= maxRow) {
                    currRow = 0, currCol += 1;
                }
                childX = eleX + currCol * cMinX + 20; //Default addition
                childY = eleY - ((maxRow -1 )/2) *cMinY + currRow * cMinY;
                allparentsData.children[i].position = {
                    x: childX,
                    y:childY
                }


                if(currCol % 2 == 0)
                    allparentsData.children[i].style["text-valign"] ="top";
                else
                    allparentsData.children[i].style["text-valign"] ="bottom";
                ancestorGraph.add(allparentsData.children[i])
                currRow += 1;
            }
        }
        ancestorGraph.fit();
        ancestorGraph.resize();
        //Nothing works if panning and zoom is disabled; since resize and fit needs to pan and zoom :P
        //Note: ancestorGraph functions needs pan and zoom to be enabled to operate keep that in mind
        ancestorGraph.zoomPanToggle( $scope.DAG.zoom); //Override the default settings
    }
        var drawChildrenAsList = function(allparentsData) {
            var cMinX = 0,
                cMinY = 35,
                currCol = 0,
                currRow = 0,
                maxRow = 15, //max rows in a coloumn
                lastColumnX = 0;
            var colMaxWidth = 0;
            if( allparentsData.children.length > 0 ) {
                var ele = ancestorGraph.getElementById("Children of "+$scope.metaInfoName)
                var eleX = ele.position().x,
                    eleY = ele.position().y;
                var childX = 0,
                    childY = 0;
                if(allparentsData.children.length < maxRow){
                    maxRow =  allparentsData.children.length;
                }
                lastColumnX = eleX;
                for (var i = 0; i < allparentsData.children.length; i++)
                {
                    if(currRow >= maxRow) {
                        currRow = 0, currCol += 1;
                        cMinX = colMaxWidth * 7;
//                        console.log("cMinX:", cMinX)
//                        console.log("colMaxWidth:", colMaxWidth)
//                        console.log("currCol:", currCol)
                        lastColumnX = lastColumnX + cMinX ;
                        colMaxWidth = 0;

                    }
                    childX = lastColumnX + cMinX;
                    childY = eleY - ((maxRow -1 )/2) *cMinY + currRow * cMinY;
                    allparentsData.children[i].position = {
                        x: childX,
                        y: childY
                    }
                    allparentsData.children[i].style["text-halign"] ="right";
                    allparentsData.children[i].style["text-valign"] ="center";
                    ancestorGraph.add(allparentsData.children[i])

                    if(allparentsData.children[i].data.id.length > colMaxWidth){
                        colMaxWidth = allparentsData.children[i].data.id.length;
                    }
                    currRow += 1;
                }
            }
            ancestorGraph.fit();
            ancestorGraph.resize();
            //Nothing works if panning and zoom is disabled; since resize and fit needs to pan and zoom :P
            //Note: ancestorGraph functions needs pan and zoom to be enabled to operate keep that in mind
            ancestorGraph.zoomPanToggle( $scope.DAG.zoom); //Override the default settings
        }

//        $scope.drawGraph = function(metaInfos){
//            console.log($scope.combinedGraph.selectedMetaInfos);
//
//            var allData = {
//                nodes: [],
//                edges: []
//            }
//
//            for(var i= 0; i< metaInfos.length;i++)
//            {
//                dataService.asyncAllParentsCS($scope.version,metaInfos[i]).then(function(d) {
////                        return allparentsData;
//                allData.nodes = allData.nodes.concat(d.nodes);
//                allData.edges = allData.edges.concat(d.edges);
//                });
////                allData.edges = allData.edges.concat(d.edges);
//            }
//            console.log(allData);
//
//            ancestorGraph( allData ).then(function( ancestorCy ){
//                cy = ancestorCy;
//                $scope.cyLoaded = true;
//                ancestorGraph.fit();
//                ancestorGraph.resize();
//                //Nothing works if panning and zoom is disabled; since resize and fit needs to pan and zoom :P
//                //Note: ancestorGraph functions needs pan and zoom to be enabled to operate keep that in mind
//                ancestorGraph.zoomPanToggle( $scope.DAG.zoom); //Override the default settings
//            });
//
//        }

//       Version updated; load the new version
         $scope.versionChange = function(version){
            $location.path('/'+version+'/'+$scope.metaInfoName);
        }

//      Update service on filter change; Check if its necessary
        $scope.filterChange = function(selectedValues){
//            $scope.filter = filterService.setSelected(selectedValues);
        }

//      Cyptoscape Related stuff;
        ancestorGraph.onClick(function(id){
          $scope.display(id)
          $scope.$apply();
        });
        $scope.reset = function(){
         ancestorGraph.reset();
        }
        $scope.zoomToggle = function(){
           if ( $scope.DAG.zoom){
                    $scope.DAG.zoom=false;
                    $scope.DAG.zoomText='Enable zoom';
           }
           else {
                  $scope.DAG.zoom=true;
                  $scope.DAG.zoomText='Disable zoom';
           }
           ancestorGraph.zoomPanToggle($scope.DAG.zoom);
        };
    };
    //The ancestorGraph service, defined using a factory
    //TODO: replace rootScope
    function ancestorGraphDeclaration($q,$location,$rootScope){
        var cy;
        var ancestorGraph = function(elem){
            var deferred = $q.defer();
            $(function(){ // on dom ready
                cy = cytoscape({
                    container: $('#cy')[0],
                    layout: {
                        name: 'dagre',
                        rankDir: 'RL'
                    },
                    elements: elem,
                    ready: function(){
                        deferred.resolve( this );
                        this.on('click', 'node', function(evt){
                            //console.log($location.path());
                            var path = angular.copy($location.path());
                            var splitPath = path.split("/");
                            $location.path(splitPath.slice(0, splitPath.length - 1).join("/") + "/" + this.id());
                            $rootScope.$apply();
                            //                        fire('onClick', [ this.id().split(" -")[0] ]);
                        });
                    },

                    //Initial viewport state;
//                    zoom: 0.5,
//                    pan: { x: 0, y: 0 },,

                    //interaction options
                    boxSelectionEnabled: true,
                    autounselectify: true,
                    zoomingEnabled: true, //Always keep true at initialization; Call f
                    panningEnabled: true,
                    style: cytoscape.stylesheet()
                        .selector('node')
                        .css({
                          'content': 'data(id)',
                          'text-opacity': 0.5,
                          'text-valign': 'bottom',
                          'text-halign': 'center',
                          'background-color': '#11479e'
                         })
                        .selector('edge')
                        .css({
                          'width': 4,
                          'target-arrow-shape': 'triangle',
                          'line-color': '#9dbaea',
                          'target-arrow-color': '#9dbaea'
                        })
                });
            }); // on dom ready
            return deferred.promise;
        };

        ancestorGraph.listeners = {};

        function fire(e, args){
            var listeners = ancestorGraph.listeners[e];
            for( var i = 0; listeners && i < listeners.length; i++ ){
                var fn = listeners[i];
                fn.apply( fn, args );
            }
        }

        function listen(e, fn){
            var listeners = ancestorGraph.listeners[e] = ancestorGraph.listeners[e] || [];
            listeners.push(fn);
        }

        ancestorGraph.resize = function(){
            if(cy.zoomingEnabled() && cy.panningEnabled()) {
                cy.resize();
            }
            else {
                var tempZoom = cy.zoomingEnabled();
                var tempPan =  cy.panningEnabled();
                cy.zoomingEnabled(true);
                cy.panningEnabled(true);
                cy.resize();
                cy.zoomingEnabled(tempZoom);
                cy.panningEnabled(tempPan);
            }
        }

        ancestorGraph.fit = function(){
//            panZoomIndependent(cy.fit);
            if(cy.zoomingEnabled() && cy.panningEnabled()) {
                cy.fit();
            }
            else {
                var tempZoom = cy.zoomingEnabled();
                var tempPan =  cy.panningEnabled();
                cy.zoomingEnabled(true);
                cy.panningEnabled(true);
                cy.fit();
                cy.zoomingEnabled(tempZoom);
                cy.panningEnabled(tempPan);
            }
        }

        ancestorGraph.reset = function(){
            if(cy.zoomingEnabled() && cy.panningEnabled()) {
                cy.reset();
            }
            else {
                var tempZoom = cy.zoomingEnabled();
                var tempPan =  cy.panningEnabled();
                cy.zoomingEnabled(true);
                cy.panningEnabled(true);
                cy.reset();
                cy.fit();
                cy.zoomingEnabled(tempZoom);
                cy.panningEnabled(tempPan);
            }
        }
        function panZoomIndependent (otherFunc){
            if(cy.zoomingEnabled() && cy.panningEnabled()) {
                otherFunc();
            }
            else {
                var tempZoom = cy.zoomingEnabled();
                var tempPan =  cy.panningEnabled();
                cy.zoomingEnabled(true);
                cy.panningEnabled(true);
                otherFunc();
                cy.zoomingEnabled(tempZoom);
                cy.panningEnabled(tempPan);
            }
        }

        ancestorGraph.zoomPanToggle = function(zoom){
//            console.log(cy);
//            console.log(cy.zoomingEnabled());
            cy.panningEnabled(zoom);
            cy.zoomingEnabled(zoom);
            cy.autoungrabify( zoom);
        };
        ancestorGraph.getElementById = function(ele){
//        console.log(ele);
            return cy.getElementById(ele);
        };

        ancestorGraph.zoom = function(zm){
            if(zm)
                return cy.zoom(zm);
            else return cy.zoom();
        };

        ancestorGraph.outerWidth= function(ele){
            return ele.width();
        }
        ancestorGraph.json = function(){
            return cy.json();
        };

        ancestorGraph.add = function(eles){
            cy.add(eles);
        };

        ancestorGraph.onClick = function(fn){
            listen('onClick', fn);
        };

        return ancestorGraph;
    };

    function masterDirective($window) { //declaration; identifier master
        function link(scope, element, attrs) { //scope we are in, element we are bound to, attrs of that element
          scope.$watch(function(){ //watch any changes to our element
//          console.log($window)
//          console.log(element)
//          console.log("Window Height: "+ angular.element($window).height()+ "  "+$window.innerHeight + "  "+$window +" Element height: " +element[0].offsetHeight  )
            scope.style = { //scope variable style, shared with our controller
//              height: ( angular.element($window).height() - element[0].offsetHeight )+'px' //set the height in style to our elements height
              height: ( $window.innerHeight - element[0].offsetHeight )+'px' //set the height in style to our elements height
              };
          });
        }
          return {
            restrict: 'AE', //describes how we can assign an element to our directive in this case like <div master></div
            link: link // the function to link to our element
          };
    };

    function queriesFilter() {
            return function (metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter ) {
                if(!searchFilter && !sectionFilter && !allParentsFilter && !metaInfoTypeFilter && !derivedFilter ) {
                    return metaItems;
                }
                else  {
                    var filtered = [];
                    for (var i = 0; i < metaItems.length; i++) {
                        var meta = metaItems[i];
                        if(!searchFilter || meta.name.indexOf(searchFilter) > -1 || meta.description.indexOf(searchFilter) > -1 )
                            if(!sectionFilter || meta.rootSectionAncestors.indexOf(sectionFilter) > -1)
                                if(!metaInfoTypeFilter || meta.kindStr.indexOf(metaInfoTypeFilter) > -1)
                                    if(!allParentsFilter || meta.allparents.indexOf(allParentsFilter) > -1)
                                        if(!derivedFilter || (derivedFilter && typeof meta.derived === 'undefined' ) || (typeof meta.derived != 'undefined'  &&  derivedFilter !=  meta.derived))
                                            filtered.push(meta);
                    }
                    return filtered;
                }
            };
        };
})();
