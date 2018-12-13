var iconObject = L.icon({
    iconUrl: './img/marker-icon.png',
    shadowSize: [50, 64],
    shadowAnchor: [4, 62],
    iconAnchor: [12, 40]
});

$(document).ready(function (e) {
    jQuery.support.cors = true;

    var mmMap = createMap('map-matching-map');//创建地图
    var mmClient = new GraphHopperMapMatching(/*{host: "https://graphhopper.com/api/1/", key: ""}*/);
    setup(mmMap, mmClient);
});

function setup(map, mmClient) {
    // TODO fetch bbox from /info
    //地图设置
    map.setView([50.9, 13.4], 9);//设定地图（设定其地理中心和缩放）
    // setView( <LatLng> center, <Number> zoom, <zoom/pan options> options? )
    var routeLayer = L.geoJson().addTo(map);//添加矢量（标记道路）到地图
    routeLayer.options = {
        // use style provided by the 'properties' entry of the geojson added by addDataToRoutingLayer
        style: function (feature) {
            return feature.properties && feature.properties.style;
        }};


    //e.target  得到的是input对象
    // e.target是dom类型
    function readSingleFile(e) {
        var file = e.target.files[0];//得到的是第一个input选择的文件
        if (!file) {
            return;
        }
        var reader = new FileReader();
        reader.onload = function (e) {
            var content = e.target.result;//发送到服务器的数据，整个文本

            var dom = (new DOMParser()).parseFromString(content, 'text/xml');//解析xml文档
            var pathOriginal = toGeoJSON.gpx(dom);////将gpx转换为GeoJSON
            // GeoJSON是一种用于编码各种地理数据结构的格式，详见https://www.npmjs.com/package/togeojson
            //https://www.npmjs.com/package/osmtogeojson
            //togeojson.js

            //http: geojson.org/geojson-spec.html

            routeLayer.clearLayers();//清除已有矢量
            if (pathOriginal.features[0]) {

                pathOriginal.features[0].properties = {style: {color: "black", weight: 2, opacity: 0.9}};//黑色原始数据

                routeLayer.addData(pathOriginal);//新增矢量图形，黑色原始数据
                //真实的gpx轨迹为黑色线表示
                $("#map-matching-response").text("calculate route match ...");//匹配后的返回信息
                $("#map-matching-error").text("");
            } else {
                $("#map-matching-error").text("Cannot display original gpx file. No trk/trkseg/trkpt elements found?");
            }

            //输入的工具类型与速度
            var vehicle = $("#vehicle-input").val();
            if (!vehicle)
                vehicle = "car";
            var gpsAccuracy = $("#accuracy-input").val();
            if (!gpsAccuracy)
                gpsAccuracy = 20;
            mmClient.vehicle = vehicle;

            //发送到服务器
            mmClient.doRequest(content,
                function (json) {//关于json  https://www.jb51.net/article/134072.htm// https://github.com/graphhopper/graphhopper/blob/master/docs/web/api-doc.md
                if (json.message) {
                    $("#map-matching-response").text("");
                    $("#map-matching-error").text(json.message);
                } else if (json.paths && json.paths.length > 0) {
                    var mm = json.map_matching;
                    var error = (100 * Math.abs(1 - mm.distance / mm.original_distance));
                    error = Math.floor(error * 100) / 100.0;
                    $("#map-matching-response").text("success with " + error + "% difference, "
                            + "distance " + Math.floor(mm.distance) + " vs. original distance " + Math.floor(mm.original_distance));
                    var matchedPath = json.paths[0];
                    var geojsonFeature = {
                        type: "Feature",
                        geometry: matchedPath.points,
                        properties: {style: {color: "#00cc33", weight: 6, opacity: 0.4}}
                    };
                    //用绿色表示匹配的结果
                    routeLayer.addData(geojsonFeature);//新增矢量图形，黑绿匹配数据

                    if (matchedPath.bbox) {
                        var minLon = matchedPath.bbox[0];
                        var minLat = matchedPath.bbox[1];
                        var maxLon = matchedPath.bbox[2];
                        var maxLat = matchedPath.bbox[3];
                        var tmpB = new L.LatLngBounds(new L.LatLng(minLat, minLon), new L.LatLng(maxLat, maxLon));
                        map.fitBounds(tmpB);
                    }
                } else {
                    $("#map-matching-error").text("unknown error");
                }
            }, {gps_accuracy: gpsAccuracy});

        };
        reader.readAsText(file);
    }

    document.getElementById('matching-file-input').addEventListener('change', readSingleFile, false);
}

GraphHopperMapMatching = function (args) {//mmClient
    this.host = "/";
    this.basePath = "match";
    // ->matching-web-bundle\src\main\java\com\graphhopper\matching\http\MapMatchingResource.java
    this.vehicle = "car";
    this.gps_accuracy = 20;
    this.data_type = "json";
    this.max_visited_nodes = 30000;
    graphhopper.util.copyProperties(args, this);
};

GraphHopperMapMatching.prototype.doRequest = function (content, callback, reqArgs) {
    var that = this;
    var args = graphhopper.util.clone(that);
    if (reqArgs)
        args = graphhopper.util.copyProperties(reqArgs, args);

    var url = args.host + args.basePath + "?vehicle=" + args.vehicle
            + "&gps_accuracy=" + args.gps_accuracy
            + "&type=" + args.data_type
            + "&max_visited_nodes=" + args.max_visited_nodes;

    if (args.key)
        url += "&key=" + args.key;

    $.ajax({
        timeout: 20000,//设置请求超时时间
        url: url,//发送请求的地址。//响应链接->绿色的匹配道路
        // ->matching-web-bundle\src\main\java\com\graphhopper\matching\http\MapMatchingResource.java
        contentType: "application/xml",//发送信息至服务器时内容编码类型
        type: "POST",//请求方式
        data: content//发送到服务器的数据
    }).done(function (json) {//$.ajax()如果执行成功，则执行.done()，funtion(e) 中的e 为返回结果
        if (json.paths) {
            for (var i = 0; i < json.paths.length; i++) {
                var path = json.paths[i];
                // 转化未编码的折线为geo json
                if (path.points_encoded) {
                    var tmpArray = graphhopper.util.decodePath(path.points, that.elevation);//togeojson.js
                    path.points = {
                        "type": "LineString",
                        "coordinates": tmpArray//经纬度
                    };
                }
            }
        }
        callback(json);
    }).fail(function (jqXHR) {

        if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
            callback(jqXHR.responseJSON);

        } else {
            callback({
                "message": "Unknown error",
                "details": "Error for " + url
            });
        }
    });
};


function createMap(divId) {//leaflet.js           https://www.cnblogs.com/shitao/tag/leaflet/
    var osmAttr = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

    var omniscale = L.tileLayer.wms('https://maps.omniscale.net/v1/mapmatching-23a1e8ea/tile', {
        ////加载wms服务的图层
        layers: 'osm',
        attribution: osmAttr + ', &copy; <a href="http://maps.omniscale.com/">Omniscale</a>'
    });

    var osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        //  L.tileLayer用于在地图中加载瓦片
        attribution: osmAttr
    });
        // L.map是在页面上创建地图并操控它的主要方法
    var map = L.map(divId, {layers: [omniscale]});//layers: 初始化后加载到地图上的图层.
    L.control.layers({"Omniscale": omniscale,//添加图层到地图
        "OpenStreetMap": osm, }).addTo(map);
    L.control.scale().addTo(map);

    return map;
}