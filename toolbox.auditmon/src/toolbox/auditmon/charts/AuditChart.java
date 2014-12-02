package toolbox.auditmon.charts;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.jfree.chart.JFreeChart;
import org.jfree.experimental.chart.swt.ChartComposite;

import com.ensoftcorp.atlas.core.log.Log;

public abstract class AuditChart {

	protected String session;
	protected String title;
	protected boolean showLegend = true;
	protected boolean showLabels = false;
	
	protected AuditChart(String session, String title){
		this.session = session;
		this.title = session + " : " + title;
	}

	public abstract JFreeChart getChart();
	
	public String getSession(){
		return session;
	}
	
	public boolean showLegendEnabled(){
		return showLegend;
	}
	
	public void enableShowLegend(boolean showLegend){
		this.showLegend = showLegend;
	}
	
	public boolean showLabelsEnabled(){
		return showLabels;
	}
	
	public void enableShowLabels(boolean showLabels){
		this.showLabels = showLabels;
	}
	
	public void show(){
		final Display display = Display.getDefault();
		display.asyncExec(new Runnable(){
			@Override
			public void run() {
				try {
					Shell shell = new Shell(display);
			        shell.setSize(600, 400);
			        shell.setLayout(new FillLayout());
			        shell.setText(session + " : " + title);
			        final ChartComposite frame = new ChartComposite(shell, SWT.NONE, getChart(), true);
			        frame.pack();
			        shell.open();
			        while (!shell.isDisposed()) {
			            if (!display.readAndDispatch())
			                display.sleep();
			        }
				} catch (Exception e){
					Log.error("An error displaying this chart has occurred.", e);
				}
			}
		});
	}

}
