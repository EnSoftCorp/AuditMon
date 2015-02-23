package com.ensoftcorp.open.auditmon.views;


import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

// TODO: Complete this view
public class AuditMonView extends ViewPart {
	public AuditMonView() {}

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "auditmon.views.AuditMonView";
	
	@Override
	public void createPartControl(Composite parent) {

		// add the start/stop audit monitoring button
		final Action startStopMonitoringAction = new Action() {
			public void run() {
				
			}
		};
		startStopMonitoringAction.setText("Start Audit Monitoring");
		startStopMonitoringAction.setToolTipText("Start Audit Monitoring");
		startStopMonitoringAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		startStopMonitoringAction.setEnabled(false);
		getViewSite().getActionBars().getToolBarManager().add(startStopMonitoringAction);
	}

	@Override
	public void setFocus() {
		this.setFocus();
	}	

}