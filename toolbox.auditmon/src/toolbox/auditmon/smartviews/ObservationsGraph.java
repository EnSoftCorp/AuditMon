package toolbox.auditmon.smartviews;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.ui.scripts.selections.AtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;


public class ObservationsGraph implements AtlasSmartViewScript {

	@Override
	public String getTitle() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void indexChanged(IProgressMonitor arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indexCleared() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public StyledResult selectionChanged(IAtlasSelectionEvent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
//	@Override
//	public String[] getSupportedEdgeTags() {
//		return new String[0];
//	}
//
//	@Override
//	public String[] getSupportedNodeTags() {
//		return new String[]{Node.PROJECT, Node.PACKAGE, Node.CLASS, Node.TYPE, Node.FIELD, Node.METHOD, Node.DATA_FLOW, Node.CONTROL_FLOW};
//	}
//
//	@Override
//	public String getTitle() {
//		return "Observations Graph";
//	}
//
//	@Override
//	public StyledResult selectionChanged(SelectionInput arg0) {
//		Highlighter h = new Highlighter();
//		return new StyledResult(Common.universe().reverseStep(Common.universe().nodesTaggedWithAny(AuditMon.OBSERVATION)), h);
//	}

}
