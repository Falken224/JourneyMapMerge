var JMMControllers = angular.module('JMMControllers', []);

JMMControllers.controller('ServerListController', ['$scope', 'Servers', function(scope, Servers) {
  scope.servers = Servers.query();

    scope.uploadMap = function() {
        var fd = new FormData()
        fd.append('file', document.getElementById('uploadfile').files[0])
        var xhr = new XMLHttpRequest()
        xhr.upload.addEventListener("progress", uploadProgress, false)
        xhr.addEventListener("load", uploadComplete, false)
        xhr.addEventListener("error", uploadFailed, false)
        xhr.addEventListener("abort", uploadCanceled, false)
        xhr.open("POST", "maps/"+scope.serverName)
        scope.progressVisible = true
        xhr.send(fd)
    };
    
    function uploadProgress(evt) {
        scope.$apply(function(){
            if (evt.lengthComputable) {
                scope.progress = Math.round(evt.loaded * 100 / evt.total)
            } else {
                scope.progress = 'unable to compute'
            }
        })
    }

    function uploadComplete(evt) {
        /* This event is raised when the server send back a response */
        scope.hasMessage=true;
        scope.message="Server "+angular.fromJson(evt.target.responseText).server+" was successfully updated.";
        scope.servers = Servers.query();
        scope.progressVisible=false;
        scope.$apply();
    }

    function uploadFailed(evt) {
        alert("There was an error attempting to upload the file.")
    }

    function uploadCanceled(evt) {
        scope.$apply(function(){
            scope.progressVisible = false
        })
        alert("The upload has been canceled by the user or the browser dropped the connection.")
    }
}])

JMMControllers.controller('MapViewController', ['$scope', '$routeParams', 'MapData', function(scope, routeParams, MapData) {
    scope.server = routeParams.server
    scope.mapdata = MapData.get({server:scope.server},function(){
        setImageURL();
    });
    scope.shardx=0;
    scope.shardy=0;
    scope.dimension=0;
    scope.layerName="day";
    
    function setImageURL()
    {
        scope.imageURL="maps/"+scope.server+"/"+
                      scope.dimension+"/"+
                      scope.layerName+"/"+
                      scope.shardx+","+scope.shardy+".png"
        scope.upImageAvailable=checkForImage(scope.shardx,scope.shardy-1);
        scope.downImageAvailable=checkForImage(scope.shardx,scope.shardy+1);
        scope.leftImageAvailable=checkForImage(scope.shardx-1,scope.shardy);
        scope.rightImageAvailable=checkForImage(scope.shardx+1,scope.shardy);
    }
    
    function checkForImage(x,y){
        var imageList = scope.mapdata["dimensions"]["DIM"+scope.dimension]["layers"][scope.layerName]["images"];
        for(var loop=0; loop<imageList.length; loop++)
        {
            if(imageList[loop]==""+x+","+y+".png") return true;
        }
        return false;
    }
    
    scope.goUp = function()
    {
        scope.shardy--;
        setImageURL();
        scope.$apply();
    }
    scope.goDown = function()
    {
        scope.shardy++;
        setImageURL();
        scope.$apply();
    }
    scope.goLeft = function()
    {
        scope.shardx--;
        setImageURL();
        scope.$apply();
    }
    scope.goRight = function()
    {
        scope.shardx++;
        setImageURL();
        scope.$apply();
    }
    
    scope.mapdata.$promise.then()
}])