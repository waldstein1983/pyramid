package edu.neu.ccs.pyramid.regression.regression_tree;

import edu.neu.ccs.pyramid.dataset.DataSet;

import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by chengli on 8/6/14.
 */
public class Splitter {
    private static final Logger logger = LogManager.getLogger();

    /**
     *
     * @param regTreeConfig
     * @param probs
     * @return best valid splitResult, possibly nothing
     */

    static Optional<SplitResult> split(RegTreeConfig regTreeConfig,
                                       DataSet dataSet,
                                       double[] labels,
                                       double[] probs){
        GlobalStats globalStats = new GlobalStats(labels,probs);
        if (logger.isDebugEnabled()){
            logger.debug("global statistics = "+globalStats);
        }

        List<Integer> featureIndices = IntStream.range(0, dataSet.getNumFeatures()).boxed().collect(Collectors.toList());


        Stream<Integer> stream = featureIndices.stream();
        if (regTreeConfig.isParallel()){
            stream = stream.parallel();
        }
        // the list might be empty
        return stream.map(featureIndex -> split(regTreeConfig, dataSet, labels, probs, featureIndex, globalStats))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(SplitResult::getReduction));
    }



    // TODO this is for active feature faster boosting

    static Optional<SplitResult> split(RegTreeConfig regTreeConfig,
                                       DataSet dataSet,
                                       double[] labels,
                                       double[] probs,
                                       List<Integer> activeFeatures,
                                       boolean fullScan){
        if(fullScan){
            GlobalStats globalStats = new GlobalStats(labels,probs);
            if (logger.isDebugEnabled()){
                logger.debug("global statistics = "+globalStats);
            }

            Comparator<Optional<SplitResult>> comparator = Comparator.comparing(optional -> -1*optional.get().getReduction());
            PriorityQueue<Optional<SplitResult>> fQueue = new PriorityQueue<>(comparator);

            for(int i=0;i<dataSet.getNumFeatures();i++){
                Optional<SplitResult> singleFeatureBest=split(regTreeConfig,dataSet,labels,probs,i,globalStats);
                if (singleFeatureBest.isPresent()){
                    fQueue.add(singleFeatureBest);
                }

            }

            //todo:full scan. so clear activeFeatures, and add elements to it.

            activeFeatures.clear();
            Optional<SplitResult> result = fQueue.peek();
            for(int i=0; i< regTreeConfig.getNumActiveFeatures();i++){
                SplitResult r = fQueue.poll().get();
                activeFeatures.add(r.getFeatureIndex());

            }
            return result;

        }else{
            GlobalStats globalStats = new GlobalStats(labels,probs);
            if (logger.isDebugEnabled()){
                logger.debug("global statistics = "+globalStats);
            }

            Stream<Integer> stream = activeFeatures.stream();
            if (regTreeConfig.isParallel()){
                stream = stream.parallel();
            }
            // the list might be empty
            return stream.map(featureIndex -> split(regTreeConfig, dataSet, labels, probs, featureIndex, globalStats))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .max(Comparator.comparing(SplitResult::getReduction));
        }
    }







    private static Optional<SplitResult> split(RegTreeConfig regTreeConfig,
                                       DataSet dataSet,
                                       double[] labels,
                                       double[] probs,
                                       int featureIndex,
                                       GlobalStats globalStats){

        return IntervalSplitter.split(regTreeConfig,dataSet,labels,
                    probs,featureIndex, globalStats);
    }


    static class GlobalStats {
        //\sum _i p_i * y_i
        private double WeightedLabelSum;
        // \sum _i p_i
        private double probabilisticCount;
        // number of elements with non-zero probabilities
        private int binaryCount;

        GlobalStats(double[] labels,
                    double[] probs) {
            for (int i=0;i<labels.length;i++){
                double label = labels[i];
                double prob = probs[i];
                WeightedLabelSum += label*prob;
                probabilisticCount += prob;
                if (prob>0){
                    binaryCount += 1;
                }
            }
        }

        public double getWeightedLabelSum() {
            return WeightedLabelSum;
        }

        public double getProbabilisticCount() {
            return probabilisticCount;
        }

        public int getBinaryCount() {
            return binaryCount;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("GlobalStats{");
            sb.append("WeightedLabelSum=").append(WeightedLabelSum);
            sb.append(", probabilisticCount=").append(probabilisticCount);
            sb.append(", binaryCount=").append(binaryCount);
            sb.append('}');
            return sb.toString();
        }
    }
}
