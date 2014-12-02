package toolbox.auditmon;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import toolbox.auditmon.AuditConstants.Granularity;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.UncheckedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.db.set.EmptyAtlasSet;
import com.ensoftcorp.atlas.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.java.core.script.Common;

/**
 * A utility class providing some convenience methods for working with an index annotated by AuditMon
 * @author Ben Holland
 */
public class AuditUtils {
	
	public enum ObservationType {
		OBSERVATION,
		START,
		STOP
	}
	
	/**
	 * An interface that represents a particular visit to an AuditMon observation node
	 * This class contains some helpers for getting timestamps and other attributes relative to the visit order
	 */
	public static abstract class AbstractObservation {
		
		protected int visitationIndex;
		protected GraphElement observationNode;
		protected String session;

		public AbstractObservation(GraphElement observationNode, String session, int visitationIndex){
			this.visitationIndex = visitationIndex;
			this.observationNode = observationNode;
			this.session = session;
		}
		
		@SuppressWarnings("unchecked")
		public long getTimestamp() {
			ArrayList<String> timestamps = (ArrayList<String>) observationNode.attr().get(session);
			return Long.parseLong(timestamps.get(visitationIndex));
		}
		
		public String getSession(){
			return session;
		}
		
		public int getVisitationIndex(){
			return visitationIndex;
		}

		public GraphElement getObservationNode(){
			return observationNode;
		}
		
		public AtlasSet<GraphElement> getObservedNodes(){
			Q context = Common.universe().edgesTaggedWithAny(AuditMon.OBSERVATION_MEMBER).retainEdges();
			Q observedNodes = Common.stepFrom(context, Common.toQ(Common.toGraph(observationNode)));
			return observedNodes.eval().nodes();
		}
		
		public abstract ObservationType getType();
		
		@Override
		public String toString(){
			if(getType() == ObservationType.START){
				return "<start-visit: "  + visitationIndex + ", " + observationNode.address().toAddressString() + ">";
			} else if(getType() == ObservationType.START){
				return "<stop-visit: "  + visitationIndex + ", " + observationNode.address().toAddressString() + ">";
			} else {
				return "<visit: "  + visitationIndex + ", " + observationNode.address().toAddressString() + ">";
			}
		}
	}

	public static class Observation extends AbstractObservation {
		public Observation(GraphElement observationNode, String session, int visitationIndex){
			super(observationNode, session, visitationIndex);
		}
		
		public Q getObservationMembers() {
			Q observationMembersContext = Common.universe().edgesTaggedWithAll(AuditMon.OBSERVATION_MEMBER);
			return Common.stepFrom(observationMembersContext, Common.toQ(Common.toGraph(observationNode)));
		}
		
		@SuppressWarnings("unchecked")
		public String getOrigin(){
			ArrayList<String> origins = (ArrayList<String>) observationNode.attr().get(session + AuditMon.OBSERVATION_ORIGIN_SUFFIX);
			return origins.get(visitationIndex);
		}

		@Override
		public ObservationType getType() {
			return ObservationType.OBSERVATION;
		}
	}
	
	public static class StartObservation extends AbstractObservation {
		public StartObservation(GraphElement observationNode, String session, int visitationIndex){
			super(observationNode, session, visitationIndex);
		}
		
		@Override
		public ObservationType getType() {
			return ObservationType.START;
		}
	}
	
	public static class StopObservation extends Observation {
		public StopObservation(GraphElement observationNode, String session, int visitationIndex){
			super(observationNode, session, visitationIndex);
		}
		
		@SuppressWarnings("unchecked")
		public String getReason(){
			ArrayList<String> reasons = (ArrayList<String>) observationNode.attr().get(session + AuditMon.STOP_REASON_SUFFIX);
			return reasons.get(visitationIndex);
		}
		
		@Override
		public ObservationType getType() {
			return ObservationType.STOP;
		}
	}
	
	/**
	 * Returns a sorted map of timestamps to visited Observations
	 * @param session
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static TreeMap<Long,AbstractObservation> getSessionObservations(String session){
		TreeMap<Long,AbstractObservation> observations = new TreeMap<Long,AbstractObservation>();
		
		// find the start node
		GraphElement startNode = null;
		AtlasSet<GraphElement> nodes = Common.universe().nodesTaggedWithAll(AuditMon.OBSERVATION, AuditMon.START).eval().nodes();
		if(!nodes.isEmpty()){
			// there should only ever be one start node
			startNode = nodes.getFirst();
		} else {
			// no start node means no observations just return an empty result
			return observations;
		}

		if(startNode.attr().get(session) == null){
			// no session for start node, just return empty result
			return observations;
		} else {
			// add the first timestamp of the session's start node to the results
			AbstractObservation observation = new StartObservation(startNode, session, 0);
			observations.put(observation.getTimestamp(), observation);
		}
		
		Q observationContext = Common.universe().edgesTaggedWithAll(AuditMon.OBSERVATION, session);
		Long edgeNumber = 1L;
		Q currentNode = Common.toQ(Common.toGraph(startNode));
		GraphElement nextEdge = null;
		
		// keep track of how many times we've seen the observation nodes in the traversal
		HashMap<GraphElement, Integer> visitationIndexMap = new HashMap<GraphElement, Integer>();
		
		// follow the observation edges in order for the session until there are no more
		// add each node we hit during the traversal to the results
		do {
			nextEdge = null;
			Q nextNodes = Common.stepTo(observationContext, currentNode);
			AtlasSet<GraphElement> nextEdges = observationContext.betweenStep(currentNode, nextNodes).eval().edges();
			for(GraphElement edge : nextEdges){
				if(edge.attr().get(session) != null){
					if(((ArrayList<String>) edge.attr().get(session)).contains(edgeNumber.toString())){
						nextEdge = edge;
						edgeNumber++;
						break;
					}
				}
			}
			if(nextEdge != null){
				GraphElement observationNode = nextEdge.getNode(EdgeDirection.TO);

				int visitationIndex = 0;
				if(visitationIndexMap.containsKey(observationNode)){
					visitationIndex = visitationIndexMap.remove(observationNode);
					visitationIndexMap.remove(visitationIndex + 1);
				} else {
					visitationIndexMap.put(observationNode, 1);
				}
				
				AbstractObservation observation = null;
				if(observationNode.tags().contains(AuditMon.START)){
					// this is a start node, there are only timestamps
					observation = new StartObservation(observationNode, session, visitationIndex);
				} else if(observationNode.tags().contains(AuditMon.STOP)){
					// this is a stop node, there are timestamps and stop reasons
					observation = new StopObservation(observationNode, session, visitationIndex); 
				} else {
					// just a normal observation node, there are timestamps, origins, and observation members
					observation = new Observation(observationNode, session, visitationIndex);
				}
				
				// add the observation to the result
				observations.put(observation.getTimestamp(), observation);
				
				// move the currentNode to the recently observed node
				currentNode = Common.toQ(Common.toGraph(observationNode));
			}
		} while(nextEdge != null);
		
		return observations;
	}
	
//	public static void merge(String session, String indexPath){
//		View v = ImportExportUtils.importGraph(indexPath);
//		Graph alginedGraph = ImportExportUtils.alignGraph(searchSpace, toAlign, ignoreProjectMatches, ignoreVarNames, addMissingEdges);
//	}
	
	/**
	 * Given a date this function zeros out the milliseconds
	 * 
	 * @param d
	 * @return
	 */
	public static Date trimToSecond(Date d) {
		Calendar c = new GregorianCalendar();
		c.setTime(d);

		// zero out the milliseconds
		c.set(Calendar.MILLISECOND, 0);

		return c.getTime();
	}
	
	/**
	 * Given a date this function zeros out the seconds and milliseconds
	 * 
	 * @param d
	 * @return
	 */
	public static Date trimToMinute(Date d) {
		Calendar c = new GregorianCalendar();
		c.setTime(d);

		// zero out the seconds and milliseconds
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);

		return c.getTime();
	}

	/**
	 * Given a date this function zeros out the minutes, seconds and
	 * milliseconds
	 * 
	 * @param d
	 * @return
	 */
	public static Date trimToHour(Date d) {
		Calendar c = new GregorianCalendar();
		c.setTime(d);

		// zero out the minutes, seconds and milliseconds
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);

		return c.getTime();
	}

	/**
	 * Given a date this function zeros out the hours, minutes, seconds and
	 * milliseconds
	 * 
	 * @param d
	 * @return
	 */
	public static Date trimToDay(Date d) {
		Calendar c = new GregorianCalendar();
		c.setTime(d);

		// zero out the hours, minutes, seconds and milliseconds
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);

		return c.getTime();
	}
	
	/**
	 * Given a program artifact (node in atlas index) and a granularity level this method returns the 
	 * node at the requested granularity
	 * @param programArtifact
	 * @param granularity
	 * @return
	 */
	public static GraphElement getNodeGranule(GraphElement programArtifact, Granularity granularity) {
		if(granularity == Granularity.PROGRAM_ARTIFACT){
			return programArtifact;
		} else if(granularity == Granularity.PARENT_CLASS){
			return classOf(programArtifact);
		} else if(granularity == Granularity.SOURCE_FILE){
			return sourceFileOf(programArtifact);
		} else if(granularity == Granularity.PACKAGE){
			return packageOf(programArtifact);
		} else {
			return projectOf(programArtifact);
		}
	}
	
	public static String getNodeGranuleDisplayName(GraphElement programArtifact, Granularity granularity){
		if(granularity == Granularity.PROGRAM_ARTIFACT){
			return programArtifact.attr().get(Node.NAME).toString();
		} 
		
		GraphElement granule = getNodeGranule(programArtifact, granularity);
		
		if(granularity == Granularity.PARENT_CLASS){
			return getQualifiedClassName(granule);
		}
		
		if(granularity == Granularity.SOURCE_FILE){
			return granule.attr().get(Node.NAME).toString() + ".java";
		}
		
		if(granularity == Granularity.PACKAGE){
			String result = granule.attr().get(Node.NAME).toString();
			return result.equals("") ? "(default package)" : result;
		}
		
		return granule.attr().get(Node.NAME).toString();
	}
	
	public static GraphElement classOf(GraphElement nodeOfInterest) {
		if(nodeOfInterest.tags().contains(Node.CLASS)){
			return nodeOfInterest;
		}
		Q nodeOfInterestQ = Common.toQ(Common.toGraph(nodeOfInterest));
		Q context = Common.universe().edgesTaggedWithAny(Edge.DECLARES).retainEdges();
		context = context.reverse(nodeOfInterestQ);
		context = context.differenceEdges(context.reverseStep(context.nodesTaggedWithAny(Node.CLASS)));
		AtlasSet<GraphElement> parentClass = context.reverse(nodeOfInterestQ).nodesTaggedWithAny(Node.CLASS).eval().nodes();
		return parentClass.isEmpty() ? null : parentClass.getFirst();
	}
	
	public static GraphElement sourceFileOf(GraphElement nodeOfInterest) {
		if(nodeOfInterest.tags().contains(Node.CLASS)){
			return nodeOfInterest;
		}
		Q nodeOfInterestQ = Common.toQ(Common.toGraph(nodeOfInterest));
		Q context = Common.universe().edgesTaggedWithAny(Edge.DECLARES).retainEdges();
		context = context.reverse(nodeOfInterestQ);
		context = context.differenceEdges(context.forwardStep(context.nodesTaggedWithAny(Node.CLASS)));
		AtlasSet<GraphElement> sourceFile = context.forward(context.nodesTaggedWithAny(Node.PROJECT)).nodesTaggedWithAny(Node.CLASS).eval().nodes();
		return sourceFile.isEmpty() ? null : sourceFile.getFirst();
	}

	public static GraphElement packageOf(GraphElement nodeOfInterest) {
		if(nodeOfInterest.tags().contains(Node.PACKAGE)){
			return nodeOfInterest;
		}
		Q nodeOfInterestQ = Common.toQ(Common.toGraph(nodeOfInterest));
		Q context = Common.universe().edgesTaggedWithAny(Edge.DECLARES).retainEdges();
		AtlasSet<GraphElement> pkg = context.reverse(nodeOfInterestQ).nodesTaggedWithAny(Node.PACKAGE).eval().nodes();
		return pkg.isEmpty() ? null : pkg.getFirst();
	}

	public static GraphElement projectOf(GraphElement nodeOfInterest) {
		if(nodeOfInterest.tags().contains(Node.PROJECT)){
			return nodeOfInterest;
		}
		Q nodeOfInterestQ = Common.toQ(Common.toGraph(nodeOfInterest));
		Q context = Common.universe().edgesTaggedWithAny(Edge.DECLARES).retainEdges();
		AtlasSet<GraphElement> project = context.reverse(nodeOfInterestQ).nodesTaggedWithAny(Node.PROJECT).eval().nodes();
		return project.isEmpty() ? null : project.getFirst();
	}
	
	/**
     * Given a class get the fully qualified class name, note here we use # for anonymous classes
     * @param element
     * @return
     */
    public static String getQualifiedClassName(GraphElement element) {
        String result = element.attr().get(Node.NAME).toString();
        boolean foundPackage = false;
        Q step = Common.toQ(Common.toGraph(element));
        while (!foundPackage) {
                step = Common.stepFrom(Common.edges(Edge.DECLARES), step);
                if (step.eval().nodes().isEmpty()) {
                        // node does not have a package parent, may be in default package
                        // honestly this shouldn't be a case, its just a sanity check
                        // in any case we should stop walking, there is nothing left to walk
                        foundPackage = true;
                } else if (!step.nodesTaggedWithAny(Node.CLASS).eval().nodes().isEmpty()) {
                        result = step.nodesTaggedWithAny(Node.CLASS).eval().nodes().getFirst().attr().get(Node.NAME).toString() + "$" + result;
                } else if (!step.nodesTaggedWithAny(Node.PACKAGE).eval().nodes().isEmpty()) {
                        foundPackage = true;
                        String packageName = step.nodesTaggedWithAny(Node.PACKAGE).eval().nodes().getFirst().attr().get(Node.NAME).toString();
                        if (!packageName.equals("")) {
                                result = packageName + "." + result;
                        }
                } else if (!step.nodesTaggedWithAny(Node.LIBRARY).eval().nodes().isEmpty()) {
                        foundPackage = true;
                        // somehow we walked past package node to library, we
                        // are done
                } else if (!step.nodesTaggedWithAny(Node.METHOD).eval().nodes().isEmpty()) {
                        // anonymous classes, using # as a separator
                        // this is a best effort so we are using the method name as a qualifier
                        // TODO: See if I can get a better name, this node is named silly in my opinion
                        result = step.eval().nodes().getFirst().attr().get(Node.NAME).toString() + "#" + result;
                } else {
                        // skip this node (unexpected node type)
                }
        }
        return result;
    }
    
    // does not include start and stop nodes
    public static Q getAllObservedNodes(String session, Granularity granularity){
    	Collection<AbstractObservation> observations = AuditUtils.getSessionObservations(session).values();
    	return getAllObservedNodes(observations, granularity);
    }
    
    // does not include start and stop nodes
    public static Q getAllObservedNodes(Collection<AbstractObservation> observations, Granularity granularity){
    	AtlasSet<GraphElement> observedNodes = new AtlasHashSet<GraphElement>();
    	for(AbstractObservation observation : observations){
    		if(observation.getType() == ObservationType.OBSERVATION){
    			for(GraphElement node : observation.getObservedNodes()){
        			GraphElement granule = AuditUtils.getNodeGranule(node, granularity);
        			if(granule != null){
        				observedNodes.add(granule);
        			}
        		}
    		}
    				
    	}
    	return Common.toQ(new UncheckedGraph(observedNodes, EmptyAtlasSet.<GraphElement> instance()));
    }
    
    // does not includes start and stop nodes
    public static Q getObservedNodesInTimeRange(String session, Long beginRange, Long endRange){
    	AtlasSet<GraphElement> observedNodesInRange = new AtlasHashSet<GraphElement>();
    	for(AbstractObservation observation : getObservationsInTimeRange(session, beginRange, endRange)){
    		if(observation.getType() == ObservationType.OBSERVATION){
    			observedNodesInRange.addAll(observation.getObservedNodes());
    		}
    	}
    	return Common.toQ(new UncheckedGraph(observedNodesInRange, EmptyAtlasSet.<GraphElement> instance()));
    }
    
    // includes start and stop nodes
    public static Set<AbstractObservation> getObservationsInTimeRange(String session, Long beginRange, Long endRange){
    	HashSet<AbstractObservation> observationsInRange = new HashSet<AbstractObservation>();
    	TreeMap<Long, AbstractObservation> observations = AuditUtils.getSessionObservations(session);
    	for(AbstractObservation observation : observations.values()){
    		if(observation.getTimestamp() >= beginRange){
    			if(observation.getTimestamp() <= endRange){
    				observationsInRange.add(observation);
        		}
    		}
    	}
    	return observationsInRange;
    }
    
    // does not include start and stop nodes
    public static Set<AbstractObservation> getObservationsInContext(String session, Q context){
    	HashSet<AbstractObservation> observationsInContext = new HashSet<AbstractObservation>();
    	TreeMap<Long, AbstractObservation> observations = AuditUtils.getSessionObservations(session);
    	for(AbstractObservation observation : observations.values()){
    		if(observation.getType() == ObservationType.OBSERVATION){
    			Q observedNodes = Common.toQ(new UncheckedGraph(observation.getObservedNodes(), EmptyAtlasSet.<GraphElement> instance()));
    			if(!observedNodes.intersection(context).eval().nodes().isEmpty()){
    				observationsInContext.add(observation);
    			}
    		}
    	}
    	return observationsInContext;
    }
    
    public static HashMap<String,Object> getAuditStatistics(String session){
    	HashMap<String,Object> stats = new HashMap<String,Object>();
    	TreeMap<Long, AbstractObservation> observations = AuditUtils.getSessionObservations(session);
    	
    	Date auditStarted = null;
    	Date auditFinished = null;
    	Long auditTime = 0L;
    	Long breakTime = 0L;
    	Integer numResumes = -1;
    	Integer numObservations = 0;
    	
    	// iterate over the observations in order and look for start/stop node visits
    	AbstractObservation lastObservation = null;
		for (AbstractObservation observation : observations.values()) {
			
			if(observation.getType() == AuditUtils.ObservationType.START){
				if(lastObservation != null){
					breakTime += observation.getTimestamp() - lastObservation.getTimestamp();
				} else {
					auditStarted = new Date(observation.getTimestamp());
				}
				lastObservation = observation;
				numResumes++;
				continue;
			}
			
			if(observation.getType() == AuditUtils.ObservationType.STOP){
				auditTime += observation.getTimestamp() - lastObservation.getTimestamp();
				auditFinished = new Date(observation.getTimestamp());
				lastObservation = observation;
				continue;
			}
			
			numObservations++;
		}
		
		stats.put("Total Audit Time", auditTime);
		stats.put("Total Break Time", breakTime);
		stats.put("Audit Started", auditStarted);
		stats.put("Audit Finished", auditFinished);
		stats.put("Number of Times Resumed", numResumes);
		stats.put("Number of Observations", numObservations);
    	
    	return stats;
    }

    public static Set<String> getSessions(){
    	HashSet<String> sessions = new HashSet<String>();
    	GraphElement startNode = null;
		AtlasSet<GraphElement> startNodes = Common.universe().nodesTaggedWithAll(AuditMon.OBSERVATION, AuditMon.START).eval().nodes();
		if(!startNodes.isEmpty()){
			// there should only ever be one start node
			startNode = startNodes.getFirst();
		} else {
			// no start node, means no sessions
			return sessions;
		}
		
		for(String key : startNode.attr().keys()){
			if(key.equals(Node.NAME)){
				continue;
			}
			sessions.add(key);
		}
		
    	return sessions;
    }
	
}
