(function() {
    'use strict';
    // to set a module
    angular.module('queryFilter', ['ngSanitize', 'ui.select','angular.filter','metaDataApp.metaFilter','metaDataApp.dataModule','mathJaxDirective'])
    .filter('searchAndQueryFilter', searchAndQueryFilter);

    function searchAndQueryFilter() {
        return function (metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter ) {
            console.log(searchFilter +sectionFilter );
            if(!searchFilter && !sectionFilter && !allParentsFilter && !metaInfoTypeFilter && !derivedFilter ) {
                return metaItems;
            }
            else  {
                var filtered = [];
                for (var i = 0; i < metaItems.length; i++) {
                    var meta = metaItems[i];
                    if(!searchFilter || meta.name.toLowerCase().indexOf(searchFilter.toLowerCase()) > -1 || meta.description.toLowerCase().indexOf(searchFilter.toLowerCase()) > -1 )
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