package toolbox.auditmon.views;


import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import toolbox.auditmon.AuditConstants;
import toolbox.auditmon.doi.DOIModel;
import util.UIUtils;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.java.core.script.Common;
import com.ensoftcorp.atlas.ui.selection.IAtlasSelectionListener;
import com.ensoftcorp.atlas.ui.selection.SelectionUtil;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;

public class DegreeOfInterestView extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "auditmon.views.DegreeOfInterestView";
	
	private TableViewer viewer;
	private boolean synchronize = false;
	private HashMap<GraphElement,Double> doi = null;
	
	class ViewContentProvider implements IStructuredContentProvider {
		
		@Override
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
			
		}
		
		@Override
		public void dispose() {
			
		}
		
		@Override
		public Object[] getElements(Object tableKeysObj) {
			try {
				@SuppressWarnings("unchecked")
				List<GraphElement> tableKeys = (List<GraphElement>) tableKeysObj;
				
				if(tableKeys.isEmpty()){
					return new String[] {"No Data"};
				}
				
				GraphElement[] result = new GraphElement[((int) tableKeys.size())];
				int i=0;
				for(GraphElement element : tableKeys){
					result[i++] = element;
				}
				return result;
			} catch (Exception e){
				return new String[] {"No Data"};
			}
		}
	}
	
	private class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		@Override
		public String getColumnText(Object obj, int index) {
			if(obj instanceof GraphElement){
				return getEntryDisplayName((GraphElement)obj);
			} else {
				return obj.toString();
			}
		}
		
		private String getEntryDisplayName(GraphElement element){
			// get node name
			String name = element.attr().get(Node.NAME).toString();
			if(element.tags().contains(Node.PACKAGE) && name.equals("")){
				name = "(default package)";
			}

			// get doi value
			Double d = (Double) doi.get(element);
	        DecimalFormat df = new DecimalFormat("#.##");
			String doiValue = df.format(d);
			
			return name + ", " + doiValue;
		}
		
		@Override
		public Image getColumnImage(Object obj, int index) {
			if(obj instanceof GraphElement){
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
			} else {
				// label will be treated as a string, its probably an informational
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_INFO_TSK);
			}
		}
	}
	
	/**
	 * Default constructor
	 */
	public DegreeOfInterestView() {
		
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));
		
		Composite detailsComposite = new Composite(parent, SWT.NONE);
		detailsComposite.setLayout(new GridLayout(2, false));
		detailsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		
		final Label sessionLabel = new Label(detailsComposite, SWT.NONE);
		sessionLabel.setText("Session:  ");
		
		final Text sessionText = new Text(detailsComposite, SWT.BORDER);
		sessionText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Label lblModelParameters = new Label(detailsComposite, SWT.NONE);
		lblModelParameters.setText("Model Parameters:  ");
		
		Composite composite = new Composite(detailsComposite, SWT.NONE);
		composite.setLayout(new GridLayout(6, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		
		Label interestIncrementLabel = new Label(composite, SWT.NONE);
		interestIncrementLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		interestIncrementLabel.setText("Interest Increment: ");
		
		final Text interestIncrementText = new Text(composite, SWT.BORDER);
		interestIncrementText.setText("1.0");
		GridData gd_interestIncrementText = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_interestIncrementText.widthHint = 50;
		interestIncrementText.setLayoutData(gd_interestIncrementText);
		
		Label decayRateLabel = new Label(composite, SWT.NONE);
		decayRateLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		decayRateLabel.setText("Decay Rate: ");
		
		final Text decayRateText = new Text(composite, SWT.BORDER);
		decayRateText.setText("0.1");
		GridData gd_decayRateText = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_decayRateText.widthHint = 50;
		decayRateText.setLayoutData(gd_decayRateText);
		
		Label interestThresholdLabel = new Label(composite, SWT.NONE);
		interestThresholdLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		interestThresholdLabel.setText("Interest Threshold: ");
		
		final Text interestThresholdText = new Text(composite, SWT.BORDER);
		interestThresholdText.setText("-10.0");
		GridData gd_interestThresholdText = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_interestThresholdText.widthHint = 50;
		interestThresholdText.setLayoutData(gd_interestThresholdText);
		
		final Label granularityLabel = new Label(detailsComposite, SWT.NONE);
		granularityLabel.setText("Granularity:  ");
		
		final Combo granularityComboBox = new Combo(detailsComposite, SWT.READ_ONLY);
		granularityComboBox.setItems(new String[] {"Program Artifact", "First Declarative Parent", "Parent Class", "Source File", "Package", "Project"});
		granularityComboBox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		granularityComboBox.select(0);
		
		// add the start/stop audit synchronize button
		final Action startStopSynchronizeAction = new Action() {
			public void run() {
				if(synchronize){
					this.setText("Synchronize");
					this.setToolTipText("Synchronize");
					this.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
					sessionText.setEnabled(true);
				} else {
					if(sessionText.getText().trim().equals("")){
						UIUtils.showMessage("Missing Session", "Please enter an AuditMon session.");
						return;
					}
					this.setText("Unsynchronize");
					this.setToolTipText("Unsynchronize");
					this.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_STOP));
					sessionText.setEnabled(false);
				}
				synchronize = !synchronize;
			}
		};
		startStopSynchronizeAction.setText("Synchronize");
		startStopSynchronizeAction.setToolTipText("Synchronize");
		startStopSynchronizeAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		getViewSite().getActionBars().getToolBarManager().add(startStopSynchronizeAction);
		
		granularityComboBox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refreshTable(sessionText, granularityComboBox, decayRateText, interestIncrementText, interestThresholdText);
			}
		});
		
		sessionText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				refreshTable(sessionText, granularityComboBox, decayRateText, interestIncrementText, interestThresholdText);
			}
		});
		
		interestThresholdText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if(interestThresholdText.getText().equals("") || interestThresholdText.getText().equals("-") || interestThresholdText.getText().equals("-.")){
					unsynchronize(sessionText, startStopSynchronizeAction);
				} else {
					try {
						Double d = Double.parseDouble(interestThresholdText.getText());
						if(d > 0){
							unsynchronize(sessionText, startStopSynchronizeAction);
							UIUtils.showMessage("Invalid Input", "Interest threshold should be less than or equal to zero.");
						} else {
							refreshTable(sessionText, granularityComboBox, decayRateText, interestIncrementText, interestThresholdText);
						}
					} catch (Exception ex){
						UIUtils.showMessage("Invalid Input", "Interest threshold must be an integer or decimal number.");
						interestThresholdText.setText("");
					}
				}
			}
		});
		
		decayRateText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if(decayRateText.getText().equals("") || decayRateText.getText().equals("-") || decayRateText.getText().equals("-.")){
					unsynchronize(sessionText, startStopSynchronizeAction);
				} else {
					try {
						Double d = Double.parseDouble(decayRateText.getText());
						if(d < 0){
							unsynchronize(sessionText, startStopSynchronizeAction);
							UIUtils.showMessage("Invalid Input", "Decay rate should be greater than or equal to zero.");
						} else {
							refreshTable(sessionText, granularityComboBox, decayRateText, interestIncrementText, interestThresholdText);
						}
					} catch (Exception ex){
						UIUtils.showMessage("Invalid Input", "Decay rate must be an integer or decimal number.");
						decayRateText.setText("");
					}
				}
			}
		});
		
		interestIncrementText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if(interestIncrementText.getText().equals("") || interestIncrementText.getText().equals("-") || interestIncrementText.getText().equals("-.")){
					unsynchronize(sessionText, startStopSynchronizeAction);
				} else {
					try {
						Double d = Double.parseDouble(interestIncrementText.getText());
						if(d <= 0){
							unsynchronize(sessionText, startStopSynchronizeAction);
							UIUtils.showMessage("Invalid Input", "Interest increment should be greater than zero.");
						} else {
							refreshTable(sessionText, granularityComboBox, decayRateText, interestIncrementText, interestThresholdText);
						}
					} catch (Exception ex){
						UIUtils.showMessage("Invalid Input", "Interest increment must be an integer or decimal number.");
						interestIncrementText.setText("");
					}
				}
			}
		});
		
		Label dividerLabel = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		dividerLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		viewer = new TableViewer(parent, SWT.BORDER);
		Table doiTable = viewer.getTable();
		doiTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		ViewContentProvider viewContentProvider = new ViewContentProvider();
		viewer.setContentProvider(viewContentProvider);

		ViewLabelProvider viewLabelProvider = new ViewLabelProvider();
		viewer.setLabelProvider(viewLabelProvider);
		
		// don't sort results
		viewer.setSorter(null);
		
		// not sure what this does, but plugin crashes without it
		viewer.setInput(getViewSite());

		// Create the help context id for the viewer's control
		PlatformUI.getWorkbench().getHelpSystem().setHelp(viewer.getControl(), "DegreeOfInterestView.viewer");
		
		final Action doubleClickAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection)selection).getFirstElement();
				if(obj instanceof GraphElement){
					UIUtils.show(Common.toQ(Common.toGraph((GraphElement)obj)), null, true, ((GraphElement)obj).attr().get(Node.NAME).toString());
				}
			}
		};
		
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
		
		// allow the view to register for selection events and update the DOI table
		SelectionUtil.addSelectionListener(new IAtlasSelectionListener(){
			@Override
			public void selectionChanged(IAtlasSelectionEvent selectionEvent) {
				if(synchronize){
					refreshTable(sessionText, granularityComboBox, decayRateText, interestIncrementText, interestThresholdText);
				}
			}
		});
	}
	
	private void unsynchronize(final Text sessionText, final Action startStopSynchronizeAction) {
		startStopSynchronizeAction.setText("Synchronize");
		startStopSynchronizeAction.setToolTipText("Synchronize");
		startStopSynchronizeAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		sessionText.setEnabled(true);
		synchronize = false;
	}
	
	private void refreshTable(final Text sessionText, final Combo granularityComboBox, 
							  final Text decayRateText, final Text interestIncrementText, 
							  final Text interestThresholdText) {
		try {
			AuditConstants.Granularity granularity = AuditConstants.Granularity.values()[granularityComboBox.getSelectionIndex()];
			doi = DOIModel.getDOIModelForSession(sessionText.getText(), 
												 granularity, 
												 Double.parseDouble(decayRateText.getText()), 
												 Double.parseDouble(interestIncrementText.getText()), 
												 Double.parseDouble(interestThresholdText.getText()));
			viewer.setInput(DOIModel.getProgramArtifactsSortedByDOI(doi));
		} catch (Exception e){
			// invalid model parameters, skipping
		}
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}
}