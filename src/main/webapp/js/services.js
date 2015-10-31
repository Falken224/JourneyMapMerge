var MapServices = angular.module('MapServices',['ngResource']);

MapServices.factory('Servers', ['$resource',
  function($resource){
    return $resource('maps', {}, {
      query: {method:'GET', params:{}, isArray:true}
    });
  }]);
  
MapServices.factory('MapData', ['$resource',
  function($resource){
    return $resource('maps/:server/data', {}, {
      get: {method:'GET'}
    });
  }]);