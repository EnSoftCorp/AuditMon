package toolbox.auditmon.smartviews;

import toolbox.auditmon.AuditMon;

import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.java.core.script.StyledResult;
import com.ensoftcorp.atlas.java.ui.scripts.selections.AtlasSmartViewScript;

public class ObservationsGraph implements AtlasSmartViewScript {
	
	@Override
	public String[] getSupportedEdgeTags() {
		return new String[0];
	}

	@Override
	public String[] getSupportedNodeTags() {
		return new String[]{Node.PROJECT, Node.PACKAGE, Node.CLASS, Node.TYPE, Node.FIELD, Node.METHOD, Node.DATA_FLOW, Node.CONTROL_FLOW};
	}

	@Override
	public String getTitle() {
		return "Observations Graph";
	}

	@Override
	public StyledResult selectionChanged(SelectionInput arg0) {
		Highlighter h = new Highlighter();
		return new StyledResult(Common.universe().reverseStep(Common.universe().nodesTaggedWithAny(AuditMon.OBSERVATION)), h);
	}

}
