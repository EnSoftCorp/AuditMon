package toolbox.auditmon.smartviews;

import java.awt.Color;

import org.eclipse.core.runtime.IProgressMonitor;

import toolbox.auditmon.AuditMon;

import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.ui.scripts.selections.AtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.atlas.ui.selection.event.IEditorAtlasSelectionEvent;


public class ObservationsGraph implements AtlasSmartViewScript {

	@Override
	public String getTitle() {
		return "Observations Graph";
	}

	@Override
	public void indexChanged(IProgressMonitor monitor) {}

	@Override
	public void indexCleared() {}

	@Override
	public StyledResult selectionChanged(IAtlasSelectionEvent atlasSelection) {
		Highlighter h = new Highlighter();
		Q selection = atlasSelection.getSelection();
		if(atlasSelection instanceof IEditorAtlasSelectionEvent){
			IEditorAtlasSelectionEvent atlasEditorSelection = (IEditorAtlasSelectionEvent) atlasSelection;
			selection = atlasEditorSelection.getIdentifier().union(atlasEditorSelection.getControlFlow(), atlasEditorSelection.getDataFlow());
		}
		h.highlight(selection, Color.DARK_GRAY);
		return new StyledResult(Common.universe().reverseStep(Common.universe().nodesTaggedWithAny(AuditMon.OBSERVATION)), h);
	}

}
