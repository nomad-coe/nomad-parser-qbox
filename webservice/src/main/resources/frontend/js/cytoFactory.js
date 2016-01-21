(function() {
    'use strict';
    // to set a module
    angular.module('cytoscapeFactory', [])
    .factory('ancestorGraph', ['$q','$location','$rootScope', ancestorGraphDeclaration]);

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

        ancestorGraph.zoomButtonSettings ={
            //zoom: Store the zoom and "pan" setting of the graph
            zoom:false,
            zoomText:'Enable zoom'
        }

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
})();