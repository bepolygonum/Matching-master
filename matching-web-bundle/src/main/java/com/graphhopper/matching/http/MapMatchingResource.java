/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.matching.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.http.WebHelper;
import com.graphhopper.matching.*;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

import static com.graphhopper.util.Parameters.Routing.*;

/**
 * Resource to use map matching of GraphHopper in a remote client application.
 *
 * @author Peter Karich
 */
@javax.ws.rs.Path("match")
public class MapMatchingResource {

    private static final Logger logger = LoggerFactory.getLogger(MapMatchingResource.class);

    private final GraphHopper graphHopper;
    private final TranslationMap trMap;

    @Inject
    public MapMatchingResource(GraphHopper graphHopper, TranslationMap trMap) {
        this.graphHopper = graphHopper;
        this.trMap = trMap;
    }

    @POST
    @Consumes({MediaType.APPLICATION_XML, "application/gpx+xml"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doGet(
            Document body,
            @Context HttpServletRequest request,
            @QueryParam(WAY_POINT_MAX_DISTANCE) @DefaultValue("1") double minPathPrecision,
            @QueryParam("type") @DefaultValue("json") String outType,
            @QueryParam(INSTRUCTIONS) @DefaultValue("true") boolean instructions,
            @QueryParam(CALC_POINTS) @DefaultValue("true") boolean calcPoints,
            @QueryParam("elevation") @DefaultValue("false") boolean enableElevation,
            @QueryParam("points_encoded") @DefaultValue("true") boolean pointsEncoded,
            @QueryParam("vehicle") @DefaultValue("car") String vehicleStr,
            @QueryParam("locale") @DefaultValue("en") String localeStr,
            @QueryParam(Parameters.DETAILS.PATH_DETAILS) List<String> pathDetails,
            @QueryParam("gpx.route") @DefaultValue("true") boolean withRoute /* default to false for the route part in next API version, see #437 */,
            @QueryParam("gpx.track") @DefaultValue("true") boolean withTrack,
            @QueryParam("gpx.waypoints") @DefaultValue("false") boolean withWayPoints,
            @QueryParam("gpx.trackname") @DefaultValue("GraphHopper Track") String trackName,
            @QueryParam("gpx.millis") String timeString,
            @QueryParam("traversal_keys") @DefaultValue("false") boolean enableTraversalKeys,
            @QueryParam(MAX_VISITED_NODES) @DefaultValue("30000") int maxVisitedNodes,
            @QueryParam("gps_accuracy") @DefaultValue("20") double gpsAccuracy) {

        boolean writeGPX = "gpx".equalsIgnoreCase(outType);
        if (body.getElementsByTagName("trk").getLength() == 0) {
            throw new IllegalArgumentException("No tracks found in GPX document. Are you using waypoints or routes instead?");
        }

        GPXFile file = new GPXFile();
        GPXFile gpxFile = file.doImport(body, 20);

        instructions = writeGPX || instructions;

        StopWatch sw = new StopWatch().start();

        //TraversalMode
        //定义在Dijkstra或类似的路由算法进行时如何遍历图。
        // 不同的选项定义了如何考虑精确的转弯限制和成本，
        // 但仍然没有通行的支持。顺便说一句:这不会在运行时完成，
        // 这将是一个预处理步骤，以避免性能损失。
        //default：mode NODE_BASED 最简单的遍历模式，但没有转弯限制或成本支持。

        AlgorithmOptions opts = AlgorithmOptions.start()//新建Builder
                .traversalMode(graphHopper.getTraversalMode())//赋值opts.traversalMode,返回Builder
                .maxVisitedNodes(maxVisitedNodes)//赋值opts.maxVisitedNodes
                .hints(new HintsMap().put("vehicle", vehicleStr))//赋值opts.hints.put(hints);
                .build();//赋值Builder.class的buildCalled

        MapMatching matching = new MapMatching(graphHopper, opts);
        matching.setMeasurementErrorSigma(gpsAccuracy);//赋值标准差
        MatchResult matchResult = matching.doWork(gpxFile.getEntries());
        //Latitude、Longitude、Elevation、time

        // TODO: Request logging and timing should perhaps be done somewhere outside
        float took = sw.stop().getSeconds();
        String infoStr = request.getRemoteAddr() + " " + request.getLocale() + " " + request.getHeader("User-Agent");
        String logStr = request.getQueryString() + ", " + infoStr + ", took:" + took + ", entries:" + gpxFile.getEntries().size();
        logger.info(logStr);

        if ("extended_json".equals(outType)) {
            return Response.ok(convertToTree(matchResult, enableElevation, pointsEncoded)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        } else {
            Translation tr = trMap.getWithFallBack(Helper.getLocale(localeStr));//翻译地区
            DouglasPeucker peucker = new DouglasPeucker().setMaxDistance(minPathPrecision);
            PathMerger pathMerger = new PathMerger().
                    setEnableInstructions(instructions).
                    setPathDetailsBuilders(graphHopper.getPathDetailsBuilderFactory(), pathDetails).
                    setDouglasPeucker(peucker).
                    setSimplifyResponse(minPathPrecision > 0);
            PathWrapper pathWrapper = new PathWrapper();
            pathMerger.doWork(pathWrapper, Collections.singletonList(matchResult.getMergedPath()), tr);

            // GraphHopper thinks an empty path is an invalid path, and further that an invalid path is still a path but
            // marked with a non-empty list of Exception objects. I disagree, so I clear it.
            pathWrapper.getErrors().clear();
            GHResponse rsp = new GHResponse();
            rsp.add(pathWrapper);

            if (writeGPX) {
                long time = timeString != null ? Long.parseLong(timeString) : System.currentTimeMillis();
                return Response.ok(rsp.getBest().getInstructions().createGPX(trackName, time, enableElevation, withRoute, withTrack, withWayPoints, Constants.VERSION), "application/gpx+xml").
                        header("Content-Disposition", "attachment;filename=" + "GraphHopper.gpx").
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            } else {
                ObjectNode map = WebHelper.jsonObject(rsp, instructions, calcPoints, enableElevation, pointsEncoded, took);

                Map<String, Object> matchStatistics = new HashMap<>();
                matchStatistics.put("distance", matchResult.getMatchLength());
                matchStatistics.put("time", matchResult.getMatchMillis());
                matchStatistics.put("original_distance", matchResult.getGpxEntriesLength());
                matchStatistics.put("original_time", matchResult.getGpxEntriesMillis());
                map.putPOJO("map_matching", matchStatistics);

                if (enableTraversalKeys) {
                    List<Integer> traversalKeylist = new ArrayList<>();
                    for (EdgeMatch em : matchResult.getEdgeMatches()) {
                        EdgeIteratorState edge = em.getEdgeState();
                        // encode edges as traversal keys which includes orientation, decode simply by multiplying with 0.5
                        traversalKeylist.add(GHUtility.createEdgeKey(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), false));
                    }
                    map.putPOJO("traversal_keys", traversalKeylist);
                }
                return Response.ok(map).
                        header("X-GH-Took", "" + Math.round(took * 1000)).
                        build();
            }
        }
    }

    static JsonNode convertToTree(MatchResult result, boolean elevation, boolean pointsEncoded) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode diary = root.putObject("diary");
        ArrayNode entries = diary.putArray("entries");
        ObjectNode route = entries.addObject();
        ArrayNode links = route.putArray("links");
        for (int emIndex = 0; emIndex < result.getEdgeMatches().size(); emIndex++) {
            ObjectNode link = links.addObject();
            EdgeMatch edgeMatch = result.getEdgeMatches().get(emIndex);
            PointList pointList = edgeMatch.getEdgeState().fetchWayGeometry(emIndex == 0 ? 3 : 2);
            final ObjectNode geometry = link.putObject("geometry");
            if (pointList.size() < 2) {
                geometry.putPOJO("coordinates", pointsEncoded ? WebHelper.encodePolyline(pointList, elevation) : pointList.toLineString(elevation));
                geometry.put("type", "Point");
            } else {
                geometry.putPOJO("coordinates", pointsEncoded ? WebHelper.encodePolyline(pointList, elevation) : pointList.toLineString(elevation));
                geometry.put("type", "LineString");
            }
            link.put("id", edgeMatch.getEdgeState().getEdge());
            ArrayNode wpts = link.putArray("wpts");
            for (GPXExtension extension : edgeMatch.getGpxExtensions()) {
                ObjectNode wpt = wpts.addObject();
                wpt.put("x", extension.getQueryResult().getSnappedPoint().lon);
                wpt.put("y", extension.getQueryResult().getSnappedPoint().lat);
                wpt.put("timestamp", extension.getEntry().getTime());
            }
        }
        return root;
    }

}
