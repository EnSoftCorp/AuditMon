package com.ensoftcorp.open.auditmon.charts;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.TreeMap;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.RectangleInsets;

import com.ensoftcorp.open.auditmon.AuditConstants.TimeUnit;
import com.ensoftcorp.open.auditmon.AuditUtils;
import com.ensoftcorp.open.auditmon.AuditUtils.AbstractObservation;
import com.ensoftcorp.open.auditmon.AuditUtils.ObservationType;

public class AuditTimesheetChart extends AuditChart {

	private TimeUnit timeUnit;
	
	public AuditTimesheetChart(String session, TimeUnit timeUnit) {
		super(session, "Audit Timesheet");
		this.timeUnit = timeUnit;
	}

	@Override
	public JFreeChart getChart() {
		TreeMap<Long, AbstractObservation> observations = AuditUtils.getSessionObservations(session);
		return createXYChart(createXYDataset(observations.values()));
	}
	
	private JFreeChart createXYChart(XYDataset dataset) {
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, // title
				"Time", // x-axis label
				"Analysis Session", // y-axis label
				dataset, // data
				showLegend, // create legend?
				true, // generate tooltips?
				false // generate URLs?
				);

		chart.setBackgroundPaint(java.awt.Color.white);

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint(java.awt.Color.lightGray);
		plot.setDomainGridlinePaint(java.awt.Color.white);
		plot.setRangeGridlinePaint(java.awt.Color.white);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);
		
		// hide the Y axis range
		ValueAxis range = plot.getRangeAxis();
		range.setVisible(false);

		XYItemRenderer r = plot.getRenderer();
		if (r instanceof XYLineAndShapeRenderer) {
			XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
			renderer.setBaseShapesVisible(true);
			renderer.setBaseShapesFilled(true);
			renderer.setDrawSeriesLineAsPath(true);
		}

		DateAxis axis = (DateAxis) plot.getDomainAxis();

		switch (timeUnit) {
		case MINUTES:
			axis.setDateFormatOverride(new SimpleDateFormat("h:mm a"));
			break;
		case HOURS:
			axis.setDateFormatOverride(new SimpleDateFormat("EEE h a"));
			break;
		case DAYS:
			axis.setDateFormatOverride(new SimpleDateFormat("MMM d"));
			break;
		default:
			break;
		}

		return chart;
	}

	private XYDataset createXYDataset(Collection<AbstractObservation> observations) {

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		TimeSeries s = null;
		int sessionNum = 1;
		
		// iterate over the observations in order and look for start/stop node visits
		for (AbstractObservation observation : observations) {
			
			// don't count start nodes as observations for this analysis
			if(observation.getType() == ObservationType.START){
				if(s != null){
					s.add(new Minute(new Date(observation.getTimestamp())), new Double(sessionNum));
					dataset.addSeries(s);
				}
				s = new TimeSeries("Session " + sessionNum);
				s.add(new Minute(new Date(observation.getTimestamp())), new Double(sessionNum));
				continue;
			}
			
			// don't count stop nodes as observations for this analysis
			if(observation.getType() == ObservationType.STOP){
				s.add(new Minute(new Date(observation.getTimestamp())), new Double(sessionNum));
				dataset.addSeries(s);
				s = new TimeSeries("Break " + sessionNum);
				s.add(new Minute(new Date(observation.getTimestamp())), new Double(sessionNum));
				sessionNum++;
				continue;
			}	
		}
		
		return dataset;
	}

}
