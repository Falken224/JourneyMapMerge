var JMMApp = angular.module('JMMergeApp',['JMMControllers','MapServices','ngRoute']);

JMMApp.config(['$routeProvider',
  function($routeProvider) {
    $routeProvider.
      when('/upload', {
        templateUrl: 'fragments/upload.html',
        controller: 'ServerListController'
      }).
      when('/viewmap/:server', {
        templateUrl: 'fragments/mapView.html',
        controller: 'MapViewController'
      }).
      otherwise({
        redirectTo: '/upload'
      });
  }]);
