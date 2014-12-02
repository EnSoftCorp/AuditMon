package toolbox.auditmon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.mutable.MutableDouble;

import toolbox.auditmon.AuditConstants.Granularity;
import toolbox.auditmon.AuditUtils.AbstractObservation;
import toolbox.auditmon.AuditUtils.ObservationType;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;

/**
 * This is a proof of concept implementation of the Mylar: a degree-of-interest model for IDEs
 * using an AuditMon session, with an added concept of model granularity
 * @author Ben Holland
 */
public class DOIModel {

	// The default DOI model parameters
	public static final double DEAFULT_DECAY_RATE = 0.1;
	public static final double DEAFULT_INTEREST_INCREASE = 1.0;
	public static final double DEAFULT_INTEREST_THRESHOLD = -10.0;
	
	/**
	 * Returns the DOI table for the session and granularity using the default model parameters
	 * @param session
	 * @param granularity
	 * @return
	 */
	public static HashMap<GraphElement,Double> getDOIModelForSession(String session, Granularity granularity){
		return getDOIModelForSession(session, granularity, DEAFULT_DECAY_RATE, DEAFULT_INTEREST_INCREASE, DEAFULT_INTEREST_THRESHOLD);
	}

	/**
	 * Returns a table of GraphElements with given granularity and the
	 * calculated degree of interest for each element for the given audit session
	 * @param session
	 * @param granularity
	 * @param decayRate
	 * @param interestIncrease
	 * @param interestThreshold
	 * @return
	 */
	public static HashMap<GraphElement,Double> getDOIModelForSession(String session, Granularity granularity, double decayRate, double interestIncrease, double interestThreshold){
		HashMap<GraphElement,MutableDouble> table = new HashMap<GraphElement,MutableDouble>();
		for(AbstractObservation observation : AuditUtils.getSessionObservations(session).values()){
			// ignore start and stop observation nodes
			if(observation.getType() == ObservationType.OBSERVATION){
				AtlasSet<GraphElement> observedNodes = observation.getObservedNodes();
				// DOI model doesn't lend itself well to observations of multiple nodes,
				// so we break up each simultaneous observations into separate observations
				for(GraphElement observedNode : observedNodes){
					GraphElement nodeOfInterest = AuditUtils.getNodeGranule(observedNode, granularity);
					// skip nodes that are too coarse for the given granularity
					if(nodeOfInterest != null){
						// add or increment node of interest
						if(table.containsKey(nodeOfInterest)){
							table.get(nodeOfInterest).add(interestIncrease);
						} else {
							table.put(nodeOfInterest, new MutableDouble(interestIncrease));
						}
						// let decay decrement old nodes
						LinkedList<GraphElement> nodesToRemove = new LinkedList<GraphElement>();
						for(Entry<GraphElement,MutableDouble> entry : table.entrySet()){
							if(!entry.getKey().equals(nodeOfInterest)){
								entry.getValue().subtract(decayRate);
							}
							if(entry.getValue().toDouble() < interestThreshold){
								nodesToRemove.add(entry.getKey());
							}
						}
						// remove nodes below interest threshold
						for(GraphElement nodeToRemove : nodesToRemove){
							table.remove(nodeToRemove);
						}
					}
				}
			}
		}
		// convert to result format
		HashMap<GraphElement,Double> doi = new HashMap<GraphElement,Double>();
		for(Entry<GraphElement,MutableDouble> entry : table.entrySet()){
			doi.put(entry.getKey(), entry.getValue().toDouble());
		}
		return doi;
	}
	
	public static List<GraphElement> getProgramArtifactsSortedByDOI(Map<GraphElement, Double> doiEntries) {
	    // Convert map to list of <GraphElement,Double> entries
	    List<Map.Entry<GraphElement, Double>> list = new ArrayList<Map.Entry<GraphElement, Double>>(doiEntries.entrySet());

	    // Sort list by descending values
	    Collections.sort(list, new Comparator<Map.Entry<GraphElement, Double>>() {
	        public int compare(Map.Entry<GraphElement, Double> o1, Map.Entry<GraphElement, Double> o2) {
	            return (o2.getValue()).compareTo(o1.getValue());
	        }
	    });

	    // Populate the result into an ordered list
	    List<GraphElement> result = new ArrayList<GraphElement>();
	    for (Map.Entry<GraphElement, Double> entry : list) {
	        result.add(entry.getKey());
	    }
	    return result;
	}
}
