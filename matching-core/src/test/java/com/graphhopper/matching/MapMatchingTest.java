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
package com.graphhopper.matching;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author Peter Karich
 * @author kodonnell
 */
@RunWith(Parameterized.class)
public class MapMatchingTest {

    public final static TranslationMap SINGLETON = new TranslationMap().doImport();

    // non-CH / CH test parameters
    private final String parameterName;
    private final TestGraphHopper hopper;
    private final AlgorithmOptions algoOptions;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> algoOptions() {
        // create hopper instance with CH enabled
        CarFlagEncoder encoder = new CarFlagEncoder();
        TestGraphHopper hopper = new TestGraphHopper();
        hopper.setDataReaderFile("../map-data/xiamen.osm");
        hopper.setGraphHopperLocation("../target/mapmatchingtest-ch");
        hopper.setEncodingManager(new EncodingManager(encoder));
        hopper.importOrLoad();

        // force CH
        AlgorithmOptions chOpts = AlgorithmOptions.start()
                .maxVisitedNodes(100000)
                .hints(new PMap().put(Parameters.CH.DISABLE, false))
                .build();

        // flexible should fall back to defaults
        AlgorithmOptions flexibleOpts = AlgorithmOptions.start()
                // TODO: fewer nodes than for CH are possible (short routes & different finish condition & higher degree graph)
                // .maxVisitedNodes(20)
                .build();

        return Arrays.asList(new Object[][]{
            {"non-CH", hopper, flexibleOpts},
            {"CH", hopper, chOpts}
        });
    }

    public MapMatchingTest(String parameterName, TestGraphHopper hopper,
                           AlgorithmOptions algoOption) {
        this.parameterName = parameterName;
        this.algoOptions = algoOption;
        this.hopper = hopper;
    }

    /**
     * TODO: split this test up into smaller units with better names?
     */
    @Test
    public void testDoWork() throws IOException {
//        List<GPXEntry> inputGPXEntries = createRandomGPXEntries(//创建两个测试gpx点
//                new GHPoint(24.4908816666667,118.147066666667),
//                new GHPoint(24.4943316666667,118.141533333333));

            MapMatching mapMatching = new MapMatching(hopper, algoOptions);//创建类mapmatching

            List<GPXEntry> inputGPXEntries = new GPXFile()
                    .doImport("./src/test/out/3400618846.gpx").getEntries();
            MatchResult mr = mapMatching.doWork(inputGPXEntries,new File("./src/test/out/3400618846.gpx"));//调用dowork函数

            PathWrapper matchGHRsp = new PathWrapper();
            //简化路径： PathSimplification
                   new PathMerger().doWork(matchGHRsp, Collections.singletonList(mr.getMergedPath()), SINGLETON.get("en"));
            InstructionList il = matchGHRsp.getInstructions();

            System.out.println( "il.size():"+il.size());
                        for (int i = 0; i < il.size(); i++) {
                            System.out.print(il.get(i).getName()+ "—>");
                        }
                        System.out.println();

        // TODO full path with 20m distortion
        // TODO full path with 40m distortion
    }
//
//    /**
//     * This test is to check behavior over large separated routes: it should
//     * work if the user sets the maxVisitedNodes large enough. Input path:
//     * https://graphhopper.com/maps/?point=51.23%2C12.18&point=51.45%2C12.59&layer=Lyrk
//     */
//    @Test
//    public void testDistantPoints() {
//        // OK with 1000 visited nodes:
//        MapMatching mapMatching = new MapMatching(hopper, algoOptions);
//        List<GPXEntry> inputGPXEntries = createRandomGPXEntries(
//                new GHPoint(51.23, 12.18),
//                new GHPoint(51.45, 12.59));
//        MatchResult mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//
//        assertEquals(57650, mr.getMatchLength(), 1);
//        assertEquals(2747796, mr.getMatchMillis(), 1);
//
//        // not OK when we only allow a small number of visited nodes:
//        AlgorithmOptions opts = AlgorithmOptions.start(algoOptions).maxVisitedNodes(1).build();
//        mapMatching = new MapMatching(hopper, opts);
//        try {
//            mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//            fail("Expected sequence to be broken due to maxVisitedNodes being too small");
//        } catch (RuntimeException e) {
//            assertTrue(e.getMessage().startsWith("Sequence is broken for submitted track"));
//        }
//    }
//
//    /**
//     * This test is to check behavior over short tracks. GPX input:
//     * https://graphhopper.com/maps/?point=51.342422%2C12.3613358&point=51.3423281%2C12.3613358&layer=Lyrk
//     */
//    @Test
//    public void testClosePoints() {
//        MapMatching mapMatching = new MapMatching(hopper, algoOptions);
//        List<GPXEntry> inputGPXEntries = createRandomGPXEntries(
//                new GHPoint(51.342422, 12.3613358),
//                new GHPoint(51.342328, 12.3613358));
//        MatchResult mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//
//        assertEquals(3, mr.getMatchLength(), 1);
//        assertEquals(284, mr.getMatchMillis(), 1);
//    }
//
//    /**
//     * This test is to check what happens when two GPX entries are on one edge
//     * which is longer than 'separatedSearchDistance' - which is always 66m. GPX
//     * input:
//     * https://graphhopper.com/maps/?point=51.359723%2C12.360108&point=51.358748%2C12.358798&point=51.358001%2C12.357597&point=51.358709%2C12.356511&layer=Lyrk
//     */
//    @Test
//    public void testSmallSeparatedSearchDistance() {
//        List<GPXEntry> inputGPXEntries = new GPXFile()
//                .doImport("./src/test/resources/tour3-with-long-edge.gpx").getEntries();
//        MapMatching mapMatching = new MapMatching(hopper, algoOptions);
//        mapMatching.setMeasurementErrorSigma(20);
//        MatchResult mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//        assertEquals(Arrays.asList("Weinligstraße", "Weinligstraße", "Weinligstraße",
//                "Fechnerstraße", "Fechnerstraße"), fetchStreets(mr.getEdgeMatches()));
//        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 11); // TODO: this should be around 300m according to Google ... need to check
//        assertEquals(mr.getGpxEntriesMillis(), mr.getMatchMillis(), 3000);
//    }
//
//    /**
//     * This test is to check that loops are maintained. GPX input:
//     * https://graphhopper.com/maps/?point=51.343657%2C12.360708&point=51.344982%2C12.364066&point=51.344841%2C12.361223&point=51.342781%2C12.361867&layer=Lyrk
//     */
//    @Test
//    public void testLoop() {
//        MapMatching mapMatching = new MapMatching(hopper, algoOptions);
//
//        // Need to reduce GPS accuracy because too many GPX are filtered out otherwise.
//        mapMatching.setMeasurementErrorSigma(40);
//
//        List<GPXEntry> inputGPXEntries = new GPXFile()
//                .doImport("./src/test/resources/tour2-with-loop.gpx").getEntries();
//        MatchResult mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//        assertEquals(
//                Arrays.asList("Gustav-Adolf-Straße", "Gustav-Adolf-Straße", "Gustav-Adolf-Straße",
//                        "Leibnizstraße", "Hinrichsenstraße", "Hinrichsenstraße",
//                        "Tschaikowskistraße", "Tschaikowskistraße"),
//                fetchStreets(mr.getEdgeMatches()));
//        assertEquals(mr.getGpxEntriesLength(), mr.getMatchLength(), 5);
//        // TODO why is there such a big difference for millis?
//        assertEquals(mr.getGpxEntriesMillis(), mr.getMatchMillis(), 6000);
//    }
//
//    /**
//     * This test is to check that loops are maintained. GPX input:
//     * https://graphhopper.com/maps/?point=51.342439%2C12.361615&point=51.343719%2C12.362784&point=51.343933%2C12.361781&point=51.342325%2C12.362607&layer=Lyrk
//     */
//    @Test
//    public void testLoop2() {
//        MapMatching mapMatching = new MapMatching(hopper, algoOptions);
//        // TODO smaller sigma like 40m leads to U-turn at Tschaikowskistraße
//        mapMatching.setMeasurementErrorSigma(50);
//        List<GPXEntry> inputGPXEntries = new GPXFile()
//                .doImport("./src/test/resources/tour-with-loop.gpx").getEntries();
//        MatchResult mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//        assertEquals(Arrays.asList("Jahnallee, B 87, B 181", "Jahnallee, B 87, B 181",
//                "Jahnallee, B 87, B 181", "Jahnallee, B 87, B 181", "Funkenburgstraße",
//                "Gustav-Adolf-Straße", "Tschaikowskistraße", "Jahnallee, B 87, B 181",
//                "Lessingstraße", "Lessingstraße"), fetchStreets(mr.getEdgeMatches()));
//    }
//
//    /**
//     * This test is to check that U-turns are avoided when it's just measurement
//     * error, though do occur when a point goes up a road further than the
//     * measurement error. GPX input:
//     * https://graphhopper.com/maps/?point=51.343618%2C12.360772&point=51.34401%2C12.361776&point=51.343977%2C12.362886&point=51.344734%2C12.36236&point=51.345233%2C12.362055&layer=Lyrk
//     */
//    @Test
//    public void testUTurns() {//检查u型转弯
//        final AlgorithmOptions algoOptions = AlgorithmOptions.start(this.algoOptions)
//                // Reduce penalty to allow U-turns
//                .hints(new PMap().put(Parameters.Routing.HEADING_PENALTY, 50))
//                .build();
//
//        MapMatching mapMatching = new MapMatching(hopper, algoOptions);
//        List<GPXEntry> inputGPXEntries = new GPXFile()
//                .doImport("./src/test/resources/tour4-with-uturn.gpx").getEntries();
//
//        // with large measurement error, we expect no U-turn
//        mapMatching.setMeasurementErrorSigma(50);
//        MatchResult mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//
//        assertEquals(Arrays.asList("Gustav-Adolf-Straße", "Gustav-Adolf-Straße", "Funkenburgstraße",
//                "Funkenburgstraße"), fetchStreets(mr.getEdgeMatches()));
//
//        // with small measurement error, we expect the U-turn
//        mapMatching.setMeasurementErrorSigma(10);
//        mr = mapMatching.doWork(inputGPXEntries, gpxFile.getName());
//
//        assertEquals(
//                Arrays.asList("Gustav-Adolf-Straße", "Gustav-Adolf-Straße", "Funkenburgstraße",
//                        "Funkenburgstraße", "Funkenburgstraße", "Funkenburgstraße"),
//                fetchStreets(mr.getEdgeMatches()));
//    }

    static List<String> fetchStreets(List<EdgeMatch> emList) {
        List<String> list = new ArrayList<String>();
        int prevNode = -1;
        List<String> errors = new ArrayList<String>();
        for (EdgeMatch em : emList) {
            String str = em.getEdgeState().getName();// + ":" + em.getEdgeState().getBaseNode() +
            // "->" + em.getEdgeState().getAdjNode();
            list.add(str);
            if (prevNode >= 0) {
                if (em.getEdgeState().getBaseNode() != prevNode) {
                    errors.add(str);
                }
            }
            prevNode = em.getEdgeState().getAdjNode();
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Errors:" + errors);
        }
        return list;
    }

    private List<GPXEntry> createRandomGPXEntries(GHPoint start, GHPoint end) {
        hopper.route(new GHRequest(start, end).setWeighting("fastest"));
        return hopper.getEdges(0);
    }

    private void printOverview(GraphHopperStorage graph, LocationIndex locationIndex,
                               final double lat, final double lon, final double length) {
        final NodeAccess na = graph.getNodeAccess();
        int node = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES).getClosestNode();
        final EdgeExplorer explorer = graph.createEdgeExplorer();
        new BreadthFirstSearch() {

            double currDist = 0;

            @Override
            protected boolean goFurther(int nodeId) {
                double currLat = na.getLat(nodeId);
                double currLon = na.getLon(nodeId);
                currDist = Helper.DIST_PLANE.calcDist(currLat, currLon, lat, lon);
                return currDist < length;
            }

            @Override
            protected boolean checkAdjacent(EdgeIteratorState edge) {
                System.out.println(edge.getBaseNode() + "->" + edge.getAdjNode() + " ("
                        + Math.round(edge.getDistance()) + "): " + edge.getName() + "\t\t , distTo:"
                        + currDist);
                return true;
            }
        }.start(explorer, node);
    }

    // use a workaround to get access to paths
    static class TestGraphHopper extends GraphHopperOSM {

        TestGraphHopper() {
            super();
            getCHFactoryDecorator().setDisablingAllowed(true);
        }
        private List<Path> paths;

        List<GPXEntry> getEdges(int index) {
            Path path = paths.get(index);
            Translation tr = getTranslationMap().get("en");
            InstructionList instr = path.calcInstructions(tr);
            return instr.createGPXList();
        }

        @Override
        public List<Path> calcPaths(GHRequest request, GHResponse rsp) {
            paths = super.calcPaths(request, rsp);
            return paths;
        }
    }
}
