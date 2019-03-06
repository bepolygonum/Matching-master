package com.graphhopper.matching.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.PathWrapper;
import com.graphhopper.matching.GPXFile;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.util.*;
import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchCommand extends Command {

    public MatchCommand() {
        super("match", "map-match one or more gpx files");
    }

    @Override
    public void configure(Subparser subparser) {
        subparser.addArgument("gpx")
                .type(File.class)
                .required(true)
                .nargs("+")
                .help("GPX file");
        subparser.addArgument("--instructions")
                .type(String.class)
                .required(false)
                .setDefault("")
                .help("Locale for instructions");
        subparser.addArgument("--max_visited_nodes")
                .type(Integer.class)
                .required(false)
                .setDefault(1000);
        subparser.addArgument("--gps_accuracy")
                .type(Integer.class)
                .required(false)
                .setDefault(40);
        subparser.addArgument("--transition_probability_beta")
                .type(Double.class)
                .required(false)
                .setDefault(2.0);
    }

    @Override
    public void run(Bootstrap bootstrap, Namespace args) {
        CmdArgs graphHopperConfiguration = new CmdArgs();
        graphHopperConfiguration.put("graph.location", "graph-cache");
        GraphHopper hopper = new GraphHopperOSM().init(graphHopperConfiguration);
        hopper.getCHFactoryDecorator().setEnabled(false);
        System.out.println("loading graph from cache");
        hopper.load(graphHopperConfiguration.get("graph.location", "graph-cache"));
        FlagEncoder firstEncoder = hopper.getEncodingManager().fetchEdgeEncoders().get(0);
        AlgorithmOptions opts = AlgorithmOptions.start().
                algorithm(Parameters.Algorithms.DIJKSTRA_BI).traversalMode(hopper.getTraversalMode()).
                weighting(new FastestWeighting(firstEncoder)).
                maxVisitedNodes(args.getInt("max_visited_nodes")).
                // Penalizing inner-link U-turns only works with fastest weighting, since
                // shortest weighting does not apply penalties to unfavored virtual edges.
                hints(new HintsMap().put("weighting", "fastest").put("vehicle", firstEncoder.toString())).
                build();
        MapMatching mapMatching = new MapMatching(hopper, opts);
        mapMatching.setTransitionProbabilityBeta(args.getDouble("transition_probability_beta"));
        mapMatching.setMeasurementErrorSigma(args.getInt("gps_accuracy"));

        StopWatch importSW = new StopWatch();
        StopWatch matchSW = new StopWatch();
        String data="";
        Translation tr = new TranslationMap().doImport().get(args.getString("instructions"));
        for (File gpxFile : args.<File>getList("gpx")) {
            try {
                importSW.start();
                List<GPXEntry> inputGPXEntries = new GPXFile().doImport(gpxFile.getAbsolutePath()).getEntries();
                importSW.stop();
                matchSW.start();
                MatchResult mr = mapMatching.doWork(inputGPXEntries,gpxFile);
                matchSW.stop();
//                System.out.println("\tmatches:\t" + mr.getEdgeMatches().size() + ", gps entries:" + inputGPXEntries.size());
//                System.out.println("\tgpx length:\t" + (float) mr.getGpxEntriesLength() + " vs " + (float) mr.getMatchLength());
//                System.out.println("\tgpx time:\t" + mr.getGpxEntriesMillis() / 1000f + " vs " + mr.getMatchMillis() / 1000f);

                File parent=new File(gpxFile.getParent());
                File result=new File(parent.getParent()+"/"+parent.getName()+"result");
                if (!result.exists()) {
                    result.mkdirs();
                }
                String outFile =result.getAbsolutePath()+"/"+gpxFile.getName()+ ".res.gpx";
               // System.out.println("export results to:" + outFile);

                InstructionList il;
                if (args.getString("instructions").isEmpty()) {
                    il = new InstructionList(null);
                } else {
                    PathWrapper matchGHRsp = new PathWrapper();
                    new PathMerger().doWork(matchGHRsp, Collections.singletonList(mr.getMergedPath()), tr);
                    il = matchGHRsp.getInstructions();
                }

                new GPXFile(mr, il).doExport(outFile);
            } catch (Exception ex) {
                importSW.stop();
                matchSW.stop();
                data += gpxFile.getName();
            }
        }
        try {
            File file = new File("out.txt");
            BufferedWriter fileWritter = null;
            fileWritter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            fileWritter.write(data);
            fileWritter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
