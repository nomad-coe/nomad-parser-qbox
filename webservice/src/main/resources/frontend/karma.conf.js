module.exports = function(config){
  config.set({

    basePath : './',

    files : [
      'scripts/bower_components/angular/angular.js',
      'scripts/bower_components/angular-route/angular-route.js',
      'scripts/bower_components/angular-mocks/angular-mocks.js',
      'js/*.js',
      'test/*.js'
    ],

    autoWatch : true,

    frameworks: ['jasmine'],

    browsers : ['Chrome'],

    plugins : [
            'karma-chrome-launcher',
            'karma-firefox-launcher',
            'karma-jasmine',
            'karma-junit-reporter'
            ],

    junitReporter : {
      outputFile: 'test_out/unit.xml',
      suite: 'unit'
    }

  });
};
