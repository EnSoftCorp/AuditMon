package toolbox.auditmon;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.indexing.IIndexListener;
import com.ensoftcorp.atlas.core.indexing.IndexingUtil;
import com.ensoftcorp.atlas.core.licensing.AtlasLicenseException;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
//import com.ensoftcorp.atlas.java.ui.selection.IAtlasSelectionListener;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.ui.selection.IAtlasSelectionListener;
import com.ensoftcorp.atlas.ui.selection.SelectionUtil;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.atlas.ui.selection.event.IEditorAtlasSelectionEvent;

public class AuditMon {

	public static final String OBSERVATION_MEMBER = "observation_member";
	public static final String OBSERVATION = "observation";
	public static final String START = "start";
	public static final String STOP = "stop";
	public static final String RESUME = "resume";
	public static final String STOP_REASON_SUFFIX = "_stop_reason";
	public static final String OBSERVATION_ORIGIN_SUFFIX = "_observation_origin";
	
	private String session;
	private GraphElement lastObservationNode = null;
	private GraphElement lastObservationEdge = null;
	
	private Q context = null;
	private File journal = null;
	
	// by default let AuditMon start in an initialized state.  AuditMon will assume 
	// its running on an already indexed workspace unless it observes otherwise from 
	// an indexOperationStarted event at which point it will become uninitialized until 
	// the indexOperationComplete event is fired
	private boolean initialized = true;
	
	// by default AuditMon will not start monitoring until told to do so
	private boolean monitoring = false;
	
	private boolean updateIndex = false; // TODO: clean up this hack, for now AuditMon is hardcoded to never touch the index!
	
	private IIndexListener indexListener = new IIndexListener() {
		@Override
		public void indexOperationCancelled(IndexOperation io) {
			// user is going to have to reindex anyway, just leave things disabled
		}

		@Override
		public void indexOperationError(IndexOperation io, Throwable t) {
			// user is going to have to reindex anyway, just leave things disabled
		}

		@Override
		public void indexOperationStarted(IndexOperation io) {
			if(io == IndexOperation.NEW_INDEX || io == IndexOperation.LOAD){
				initialized = false;
				monitoring = false;
				lastObservationNode = null;
				lastObservationEdge = null;
			}
		}

		@Override
		public void indexOperationComplete(IndexOperation io) {
			if(io == IndexOperation.NEW_INDEX || io == IndexOperation.LOAD){
				initialized = true; // index is ready
			}
		}
	};
	
	IAtlasSelectionListener selectionListener = new IAtlasSelectionListener(){
		@Override
		public void selectionChanged(IAtlasSelectionEvent atlasSelection) {
			if(monitoring){
				try {
					long currentTime = System.currentTimeMillis();
					// if for some reason the event originates from an unregistered site, just call it a selection
					String origin = "selection"; 
					
					// actual name of the editor (ie "Base64.java") or graph (ie "Graph 5")
					String originName =  atlasSelection.getContributingPart().getTitle();
					// the origin type (ie Atlas Graph or Java Editor)
					String originType = atlasSelection.getContributingPart().getSite().getRegisteredName();
					origin = originType + ":" + originName;
					
					// get the selection (or all possible resolved selections)
					Q selection = atlasSelection.getSelection();
					if(atlasSelection instanceof IEditorAtlasSelectionEvent){
						IEditorAtlasSelectionEvent atlasEditorSelection = (IEditorAtlasSelectionEvent) atlasSelection;
						selection = atlasEditorSelection.getIdentifier().union(atlasEditorSelection.getControlFlow(), atlasEditorSelection.getDataFlow());
					}
					
					// make the observation
					makeObservation(selection, currentTime, origin);
				} catch (Exception e){
					Log.error("An error has occured in AuditMon.", e);
				}
			}
		}					
	};

	/**
	 * Creates a named AuditMon session
	 * By default this constructor registers selection listeners
	 * Assumes no journaling
	 * @param session
	 */
	public AuditMon(String session){
		initAuditMon(session, null, true);
	}
	
	/**
	 * Creates a named AuditMon session
	 * This constructor provides functionality to prevent registration of selection listeners
	 * Assumes no journaling
	 * @param session
	 */
	public AuditMon(String session, boolean registerSelectionListener){
		initAuditMon(session, null, registerSelectionListener);
	}
	
	/**
	 * Creates a named AuditMon session
	 * By default this constructor registers selection listeners
	 * @param session
	 */
	public AuditMon(String session, String journalFilePath){
		initAuditMon(session, journalFilePath, true);
	}
	
	/**
	 * Creates a named AuditMon session
	 * This constructor provides functionality to prevent registration of selection listeners
	 * @param session
	 */
	public AuditMon(String session, String journalFilePath, boolean registerSelectionListener){
		initAuditMon(session, journalFilePath, registerSelectionListener);
	}
	
	// just a little helper method for initializing AuditMon
	private void initAuditMon(String session, String journalFilePath, boolean registerSelectionListener){
		this.session = session;
		
		if(journalFilePath != null){
			File file = new File(journalFilePath);
			file.getParentFile().mkdirs();
			journal = file;
		}
		
		restoreSession(session);
		registerIndexListener();
		if(registerSelectionListener){
			registerSelectionListener();
		}
	}
	
	/**
	 * Saves a copy of the index to the given path
	 * @param path
	 * @throws InterruptedException
	 * @throws AtlasLicenseException 
	 */
	public void saveIndex(String path) throws InterruptedException, AtlasLicenseException {
		IndexingUtil.saveIndex(path).join();
	}
	
	/**
	 * Updates the current index
	 * @throws IOException
	 * @throws AtlasLicenseException 
	 */
	public void saveIndex() throws IOException, AtlasLicenseException {
		IndexingUtil.saveIndex();
	}
	
	/**
	 * Returns the last observation node as a Q
	 * @return
	 */
	public Q getLastObservation(){
		return Common.toQ(Common.toGraph(lastObservationNode));
	}
	
	/**
	 * Forces a restoration of the current session from the index
	 * This method should be called after any third party modifications to the index
	 * that involve observation nodes or edges
	 * @param session
	 */
	public void restoreSession(String session){
		lastObservationNode = findLastObservationNodeForSession(session);
		lastObservationEdge = findLastObservationEdgeForSession(session);
	}
	
	/**
	 * Registers the index listener
	 */
	public void registerIndexListener(){
		IndexingUtil.addListener(indexListener);
	}
	
	/**
	 * Unregisters the index listener
	 */
	public void unregisterIndexListener(){
		IndexingUtil.removeListener(indexListener);
	}
	
	/**
	 * Registers the index listener
	 */
	public void registerSelectionListener(){
		SelectionUtil.addSelectionListener(selectionListener);
	}
	
	/**
	 * Unregisters the index listener
	 */
	public void unregisterSelectionListener(){
		SelectionUtil.removeSelectionListener(selectionListener);
	}
	
	/**
	 * Returns a boolean true if the AuditMon session is observing observations
	 * @return
	 */
	public boolean isMonitoring(){
		return monitoring;
	}
	
	/**
	 * Returns true if journaling is enabled for this audit session
	 * @return
	 */
	public boolean isJournalingEnabled(){
		return journal != null;
	}

	private void journalStart(Long timestamp) throws IOException {
		FileWriter writer = new FileWriter(journal, true);
		writer.write(session + ",start," + timestamp.toString() + "\n");
		writer.close();
	}
	
	private void journalStop(Long timestamp, String reason) throws IOException {
		FileWriter writer = new FileWriter(journal, true);
		writer.write(session + ",stop," + timestamp.toString() + "," + reason + "\n");
		writer.close();
	}
	
	private void journalObservation(AtlasSet<GraphElement> nodes, Long timestamp, String origin) throws IOException {
		FileWriter writer = new FileWriter(journal, true);
		String observedNodes = "";
		boolean isFirst = true;
		for(GraphElement node : nodes){
			if(isFirst){
				observedNodes += node.address().toAddressString();
				isFirst = false;
			} else {
				observedNodes += "," + node.address().toAddressString();
			}
		}
		writer.write(session + ",observation," + timestamp.toString() + "," + origin + ",<" + observedNodes + ">" + "\n");
		writer.close();
	}
	
	/**
	 * Returns the name of this AuditMon session
	 * @return
	 */
	public String getSessionName(){
		return session;
	}
	
	/**
	 * Removes any observation filters, this AuditMon instance will 
	 * observe selections from anywhere in the universe.
	 */
	public void clearObservationFilter(){
		context = null;
	}
	
	/**
	 * Sets a context filter so that this AuditMon instance only makes
	 * observations if the observation is completely contained in the 
	 * given context.
	 * @param context
	 */
	public void setObservationFilter(Q context){
		this.context = context;
	}
	
	/**
	 * Enables observation monitoring
	 * Returns boolean true if monitoring was successfully started
	 * @return
	 */
	public boolean start(){
		return start(System.currentTimeMillis());
	}
	
	/**
	 * Enables observation monitoring
	 * Returns boolean true if monitoring was successfully started
	 * @param timestamp
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public boolean start(Long timestamp){
		if(initialized){
			monitoring = true;

			if(journal != null){
				try {
					journalStart(timestamp);
				} catch (IOException e) {
					// what happens when the backup to the backup fails?
				}
			}
			
			// may need to short circuit if just journaling
			if(updateIndex == false){
				return true;
			}
			
			// find or create the start node
			GraphElement startNode = null;
			AtlasSet<GraphElement> nodes = Common.universe().nodesTaggedWithAll(OBSERVATION,START).eval().nodes();
			if(!nodes.isEmpty()){
				// there should only be one start node
				startNode = nodes.getFirst();
			} else {
				// create the start node
				startNode = Graph.U.createNode();
				startNode.attr().put(Node.NAME, START);
				startNode.tags().add(OBSERVATION);
				startNode.tags().add(START);
			}

			if(lastObservationNode != null){
				// this is not our first start for this session...
				if(lastObservationNode.tags().contains(STOP)){
					// we are resuming a session, find or create the resume edge from stop to start
					GraphElement resumeEdge = null;
					
					// first check to see if an edge from the stop node already exists to the start node
					Q context = Common.universe().edgesTaggedWithAny(OBSERVATION).retainEdges();
					Q from = Common.toQ(Common.toGraph(lastObservationNode));
					Q to = Common.universe().nodesTaggedWithAll(OBSERVATION, START);
					AtlasSet<GraphElement> edges = context.betweenStep(from, to).eval().edges();
					if(!edges.isEmpty()){
						// there should only be one observation edge between the stop and start nodes
						resumeEdge = edges.getFirst();
						startNode = resumeEdge.getNode(EdgeDirection.TO);
					} else {
						// no edge go ahead and create it
						resumeEdge = Graph.U.createEdge(lastObservationNode, startNode);
						resumeEdge.tags().add(OBSERVATION);
						resumeEdge.tags().add(RESUME);
						resumeEdge.attr().put(Edge.NAME, OBSERVATION);
					}

					// add the observation edge number for the session
					addSessionEdgeNumber(resumeEdge);
					
					// add the session tag to the resume edge if its not there already
					if(!resumeEdge.tags().contains(session)){
						resumeEdge.tags().add(session);
					}
					
					lastObservationEdge = resumeEdge;
				} else {
					// TODO: Consider if this is the right way to handle this case
					// last session was not closed properly...
					// for now just letting it be, we've turned 
					// on monitoring and the session will resume without a stop -> start sequence
					return monitoring;
				}
			} else {
				// this is the first node of the session, all we need to is add the start node and timestamps
				// start node is already created, timestamps will need to be added and that happens regardless below
			}
			
			// add the start time for this session
			if(startNode.attr().get(session) == null){
				// this session does not exist on the start node, add it
				ArrayList<String> timestamps = new ArrayList<String>();
				timestamps.add(timestamp.toString());
				startNode.attr().put(session, timestamps);
			} else {
				// this start node has this session already, add the stop time to the node
				((ArrayList<String>)startNode.attr().get(session)).add(timestamp.toString());
			}
			
			// add the session tag to the start node if it is not there already
			if(!startNode.tags().contains(session)){
				startNode.tags().add(session);
			}
			
			lastObservationNode = startNode;
		}
		return monitoring;
	}

	/**
	 * Stops monitoring for observations and inserts a stop node with the reason of "stop"
	 * This method unconditionally stops audit monitoring (if it is currently monitoring)
	 */
	public void stop(){
		stop("stop");
	}
	
	/**
	 * Stops monitoring for observations and inserts a stop node with the given reason
	 * This method unconditionally stops audit monitoring (if it is currently monitoring)
	 * @param reason
	 */
	public void stop(String reason){
		stop(System.currentTimeMillis(), reason);
	}
	
	/**
	 * Stops monitoring for observations and inserts a stop node with the given reason and timestamp
	 * This method unconditionally stops audit monitoring observation events (if it is currently monitoring)
	 * @param timestamp
	 * @param reason
	 */
	@SuppressWarnings("unchecked")
	public void stop(Long timestamp, String reason){
		// you can't stop monitoring twice, that's just silly
		// no reason to add another stop node
		if(monitoring){
			monitoring = false;
			// if lastSelectionNode is null then the session never really started
			// or if it has started it never really "restarted"
			// so all we have to do is turn off monitoring
			if(lastObservationNode != null){
				
				if(journal != null){
					try {
						journalStop(timestamp, reason);
					} catch (IOException e) {
						// what happens when the backup to the backup fails?
					}
				}
				
				// may need to short circuit if just journaling
				if(updateIndex == false){
					return;
				}
				
				// find or create the stop node and edge
				GraphElement stopNode = null;
				GraphElement stopEdge = null;
				
				// first check to see if an edge to the stop node already exists from the last observation
				Q context = Common.universe().edgesTaggedWithAny(OBSERVATION).retainEdges();
				Q from = Common.toQ(Common.toGraph(lastObservationNode));
				Q to = Common.universe().nodesTaggedWithAll(OBSERVATION, STOP);
				AtlasSet<GraphElement> edges = context.betweenStep(from, to).eval().edges();
				if(!edges.isEmpty()){
					// there should only be one observation edge between two observation nodes
					stopEdge = edges.getFirst();
					stopNode = stopEdge.getNode(EdgeDirection.TO);
				} else {
					// the stop edge does not exist, but the stop node might
					AtlasSet<GraphElement> nodes = Common.universe().nodesTaggedWithAll(OBSERVATION,STOP).eval().nodes();
					if(!nodes.isEmpty()){
						// there should only be one stop node
						stopNode = nodes.getFirst();
					} else {
						// create the stop node it must be our first stop
						stopNode = Graph.U.createNode();
						stopNode.attr().put(Node.NAME, STOP);
						stopNode.tags().add(OBSERVATION);
						stopNode.tags().add(STOP);
					}
					
					// still need to create the observation edge
					stopEdge = Graph.U.createEdge(lastObservationNode, stopNode);
					stopEdge.tags().add(OBSERVATION);
					stopEdge.attr().put(Edge.NAME, OBSERVATION);
				}
				
				// add the session tag to the stop node and edge if its not there already
				if(!stopNode.tags().contains(session)){
					stopNode.tags().add(session);
				}
				if(!stopEdge.tags().contains(session)){
					stopEdge.tags().add(session);
				}
				
				// add the stop time for this session
				if(stopNode.attr().get(session) == null){
					// this session does not exist on the stop node, add it
					ArrayList<String> timestamps = new ArrayList<String>();
					timestamps.add(timestamp.toString());
					stopNode.attr().put(session, timestamps);
				} else {
					// this stop node has this session already, add the stop time to the node
					((ArrayList<String>)stopNode.attr().get(session)).add(timestamp.toString());
				}
				
				// add the stop reason for this session
				if(stopNode.attr().get(session + STOP_REASON_SUFFIX) == null){
					// this session does not exist on the stop node, add it
					ArrayList<String> reasons = new ArrayList<String>();
					reasons.add(reason);
					stopNode.attr().put(session + STOP_REASON_SUFFIX, reasons);
				} else {
					// this stop node has this session already, add the stop time to the node
					((ArrayList<String>)stopNode.attr().get(session + STOP_REASON_SUFFIX)).add(reason);
				}
				
				// add the observation edge number for the session
				addSessionEdgeNumber(stopEdge);
				
				// update the last observation node and edge
				lastObservationNode = stopNode;
				lastObservationEdge = stopEdge;
			}
		}
	}
	
	/**
	 * Records an observation of the given set of nodes in the index for the current session
	 * This observation origin defaults to "manual"
	 * @param observation
	 */
	public void makeObservation(Q observation){
		makeObservation(observation, "manual");
	}
	
	/**
	 * Records an observation of the given set of nodes in the index for the current session
	 * @param observation
	 * @param origin
	 */
	public void makeObservation(Q observation, String origin){
		makeObservation(observation, System.currentTimeMillis(), origin);
	}
	
	/**
	 * Records an observation of the given set of nodes in the index for the current session
	 * If edges are present in the Q then the observation will include both nodes defining the edge
	 * @param observation
	 * @param timestamp
	 * @param origin
	 */
	@SuppressWarnings("unchecked")
	public void makeObservation(Q observation, Long timestamp, String origin){
		if(monitoring){
			// make sure the element set only contains nodes
			observation = observation.retainNodes();
			AtlasSet<GraphElement> nodeSet = observation.eval().nodes();
			
			// if selection is empty just skip it
			if(nodeSet.isEmpty()){
				return;
			}

			// if there is an observation filter, make sure the observation is contained in the context
			if(context != null){
				if(observation.intersection(context).eval().nodes().size() != nodeSet.size()){
					return;
				}
			}
			
			// ignore selections of selections
			for(GraphElement node : nodeSet){
				if(node.tags().contains(OBSERVATION)){
					return;
				}
			}
			
			// TODO: Seems stale graph element references are highly elusive :\
			// this is extra defensive check for stale graph elements
			// This check is not catching them for whatever reason
			// This situation will result in a graph that has a selection -> selection edge with no member edges
			// Just going to assume they don't happen for now...
//			UniverseGraph ug = UniverseGraph.getInstance();
//			UniverseGraphElementSet universeNodes = ug.nodes();
//			for(GraphElement node : nodeSet){
//				if(universeNodes.getAt(node.address()) == null){
//					// stale graph selection, ignore it
//					// we can still pick up SC selections resulting from the stale graph
//					return;
//				}
//			}

			// skip immediately repeated observations
			if(lastObservationNode != null){
				Q memberEdges = Common.universe().edgesTaggedWithAny(OBSERVATION_MEMBER).retainEdges();
				if(memberEdges.predecessors(Common.toQ(Common.toGraph(lastObservationNode))).eval().isomorphic(observation.eval())){
					return; // last observation is the same as the current observation, do nothing
				}
			}
			
			// make the observation
			if(journal != null){
				try {
					journalObservation(nodeSet, timestamp, origin);
				} catch (IOException e) {
					// what happens when the backup to the backup fails?
				}
			}
			
			// may need to short circuit if just journaling
			if(updateIndex == false){
				return;
			}
			
			GraphElement observationNode = findObservationNode(observation);
			if(observationNode == null){
				// observation node does not exist, lets create it
				observationNode = Graph.U.createNode();
				observationNode.attr().put(Node.NAME, OBSERVATION);
				observationNode.tags().add(OBSERVATION);
				
				// add the session observation timestamp
				ArrayList<String> timestamps = new ArrayList<String>();
				timestamps.add(timestamp.toString());
				observationNode.attr().put(session, timestamps);
				
				// add the session observation origin
				ArrayList<String> origins = new ArrayList<String>();
				origins.add(origin);
				observationNode.attr().put(session + OBSERVATION_ORIGIN_SUFFIX, origins);
				
				// connect the observation members to the observation node
				for(GraphElement observationMember : observation.eval().nodes()){
					GraphElement memberEdge = Graph.U.createEdge(observationMember, observationNode);
					memberEdge.tags().add(OBSERVATION_MEMBER);
					memberEdge.attr().put(Edge.NAME, OBSERVATION_MEMBER);
				}
			} else {
				// observation node exists, update it
				// add the session observation timestamp
				if(observationNode.attr().containsKey(session)){
					((ArrayList<String>) observationNode.attr().get(session)).add(timestamp.toString());
				} else {
					// observation node is being reused, but not for this session
					ArrayList<String> timestamps = new ArrayList<String>();
					timestamps.add(timestamp.toString());
					observationNode.attr().put(session, timestamps);
				}
				
				// add the session observation origin
				if(observationNode.attr().containsKey(session + OBSERVATION_ORIGIN_SUFFIX)){
					((ArrayList<String>) observationNode.attr().get(session + OBSERVATION_ORIGIN_SUFFIX)).add(origin);
				} else {
					// observation node is being reused, but not for this session
					ArrayList<String> origins = new ArrayList<String>();
					origins.add(origin);
					observationNode.attr().put(session + OBSERVATION_ORIGIN_SUFFIX, origins);
				}
			}
			// add session tag to node
			if(!observationNode.tags().contains(session)){
				observationNode.tags().add(session);
			}
			
			// find or create the observation edge
			GraphElement observationEdge = findObservationEdge(lastObservationNode, observationNode);
			if(observationEdge == null){
				// observation edge does not exist, create it
				observationEdge = Graph.U.createEdge(lastObservationNode, observationNode);
				observationEdge.tags().add(OBSERVATION);
				observationEdge.attr().put(Edge.NAME, OBSERVATION);
			}
			
			// add session tag to edge
			if(!observationEdge.tags().contains(session)){
				observationEdge.tags().add(session);
			}
			
			// add edge number to observation edge
			addSessionEdgeNumber(observationEdge);
			
			// update the last observation edge
			lastObservationEdge = observationEdge;
			
			// update the last observation node
			lastObservationNode = observationNode;
		}
	}

	/**
	 * Adds the next incremental edge number to the given edge for the current session
	 * @param observationEdge
	 */
	@SuppressWarnings("unchecked")
	private void addSessionEdgeNumber(GraphElement observationEdge) {
		Long edgeNumber = 1L;
		if(lastObservationEdge != null) {
			// edgeNumber = max(session edge numbers) + 1
			ArrayList<String> edgeNumbers = ((ArrayList<String>) lastObservationEdge.attr().get(session));
			if(edgeNumbers != null && !edgeNumbers.isEmpty()){
				edgeNumber = Long.parseLong(edgeNumbers.get(edgeNumbers.size()-1)) + 1;
			}
		}
		if(observationEdge.attr().get(session) == null){
			// this session does not exist on the edge, add it
			ArrayList<String> edgeNumbers = new ArrayList<String>();
			edgeNumbers.add(edgeNumber.toString());
			observationEdge.attr().put(session, edgeNumbers);
		} else {
			// this edge has this session already, add the edge number to the node
			((ArrayList<String>)observationEdge.attr().get(session)).add(edgeNumber.toString());
		}
	}

	/**
	 * Returns an observation edge from the given firstObservationNode to the given secondObservationNode
	 * if the edge exists or returns null if the edge does not exist
	 * Assumes it is only possible for a single edge to exist from first to second observation node
	 * @param firstObservationNode
	 * @param secondObservationNode
	 * @return
	 */
	private GraphElement findObservationEdge(GraphElement firstObservationNode, GraphElement secondObservationNode) {
		Q context = Common.universe().edgesTaggedWithAll(OBSERVATION).retainEdges();
		Q first = Common.toQ(Common.toGraph(firstObservationNode));
		Q second = Common.toQ(Common.toGraph(secondObservationNode));
		AtlasSet<GraphElement> edges = context.betweenStep(first, second).eval().edges();
		return edges.isEmpty() ? null : edges.getFirst();
	}
	
	/**
	 * Finds a observation node that has member edges to each and only every GE in the node set
	 * Returns null if no observation node exists
	 * @param nodes
	 * @return
	 */
	public static GraphElement findObservationNode(Q nodes) {
		Q memberEdges = Common.universe().edgesTaggedWithAny(OBSERVATION_MEMBER).retainEdges();
		long numObservationMembers = nodes.eval().nodes().size();
		Q reachableObservations = memberEdges.successors(nodes);
		GraphElement observationNode = null;
		for(GraphElement reachableObservation : reachableObservations.eval().nodes()){
			long numMembersInReachableObservation = memberEdges.predecessors(Common.toQ(Common.toGraph(reachableObservation))).eval().nodes().size();
			if(numMembersInReachableObservation == numObservationMembers){
				observationNode = reachableObservation;
			}
		}
		return observationNode;
	}
	
	/**
	 * Finds the last observation node for the given session or returns null if none exists
	 * @param session
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static GraphElement findLastObservationNodeForSession(String session){
		GraphElement result = null;
		Q observationNodes = Common.universe().nodesTaggedWithAll(OBSERVATION, session);
		for(GraphElement observationNode : observationNodes.eval().nodes()){
			if(result == null){
				result = observationNode;
			} else {
				ArrayList<String> observationNodeTimestamps = ((ArrayList<String>)observationNode.attr().get(session));
				ArrayList<String> resultNodeTimestamps = ((ArrayList<String>)result.attr().get(session));
				if(Long.parseLong(observationNodeTimestamps.get(observationNodeTimestamps.size()-1)) > Long.parseLong(resultNodeTimestamps.get(resultNodeTimestamps.size()-1))){
					result = observationNode;
				}
			}
		}
		return result;
	}
	
	/**
	 * Finds the first observation node for the given session or returns null if none exists
	 * @param session
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static GraphElement findFirstObservationNodeForSession(String session){
		GraphElement result = null;
		Q observationNodes = Common.universe().nodesTaggedWithAll(OBSERVATION, session);
		for(GraphElement observationNode : observationNodes.eval().nodes()){
			if(result == null){
				result = observationNode;
			} else {
				ArrayList<String> observationNodeTimestamps = ((ArrayList<String>)observationNode.attr().get(session));
				ArrayList<String> resultNodeTimestamps = ((ArrayList<String>)result.attr().get(session));
				if(Long.parseLong(observationNodeTimestamps.get(0)) < Long.parseLong(resultNodeTimestamps.get(0))){
					result = observationNode;
				}
			}
		}
		return result;
	}
	
	/**
	 * Finds the last observation edge for the given session or returns null if none exists
	 * @param session
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static GraphElement findLastObservationEdgeForSession(String session){
		GraphElement result = null;
		Q observationEdges = Common.universe().edgesTaggedWithAll(OBSERVATION, session);
		for(GraphElement observationEdge : observationEdges.eval().edges()){
			if(result == null){
				result = observationEdge;
			} else {
				ArrayList<String> observationEdgeNumbers = ((ArrayList<String>)observationEdge.attr().get(session));
				ArrayList<String> resultEdgeNumbers = ((ArrayList<String>)result.attr().get(session));
				if(Long.parseLong(observationEdgeNumbers.get(observationEdgeNumbers.size()-1)) > Long.parseLong(resultEdgeNumbers.get(resultEdgeNumbers.size()-1))){
					result = observationEdge;
				}
			}
		}
		return result;
	}
}
