package toolbox.auditmon.charts;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.Minute;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.RectangleInsets;

import toolbox.auditmon.AuditConstants.Granularity;
import toolbox.auditmon.AuditConstants.TimeUnit;
import toolbox.auditmon.AuditUtils;
import toolbox.auditmon.AuditUtils.AbstractObservation;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;

public class RepeatObservationsChart extends AuditChart {

	private TimeUnit timeUnit;
	private Granularity granularity;
	
	public RepeatObservationsChart(String session, TimeUnit timeUnit, Granularity granularity) {
		super(session, "Repeat");
		this.timeUnit = timeUnit;
		this.granularity = granularity;

		if (granularity == Granularity.PROGRAM_ARTIFACT) {
			title += " Program Artifact Observations";
		} else if (granularity == Granularity.PARENT_CLASS) {
			title += " Class Observations";
		} else if (granularity == Granularity.SOURCE_FILE) {
			title += " Source File Observations";
		} else if (granularity == Granularity.PACKAGE) {
			title += " Package Observations";
		} else if (granularity == Granularity.PROJECT) {
			title += " Project Observations";
		}
		
		if (timeUnit == TimeUnit.SECONDS) {
			title += " Per Second";
		} else if (timeUnit == TimeUnit.MINUTES) {
			title += " Per Minute";
		} else if (timeUnit == TimeUnit.HOURS) {
			title += "Per Hour";
		} else if (timeUnit == TimeUnit.DAYS) {
			title += "Per Day";
		}
	}

	@Override
	public JFreeChart getChart() {
		TreeMap<Long, AbstractObservation> observations = AuditUtils.getSessionObservations(session);
		HashSet<HashSet<GraphElement>> observationsSeen = new HashSet<HashSet<GraphElement>>();
		HashMap<Long,Integer> totalObservationsPerTimeUnit = new HashMap<Long,Integer>();
		HashMap<Long,Integer> uniqueObservationsPerTimeUnit = new HashMap<Long,Integer>();
		// iterate over the observations in order and group number of new and
		// seen observations into buckets according to the time unit
		for (Entry<Long, AbstractObservation> entry : observations.entrySet()) {
			
			// don't count start nodes as observations for this analysis
			if(entry.getValue().getType() == AuditUtils.ObservationType.START){
				continue;
			}
			
			// don't count stop nodes as observations for this analysis
			if(entry.getValue().getType() == AuditUtils.ObservationType.STOP){
				continue;
			}
			
			// consider an observation to be a group of nodes at the requested granularity level
			HashSet<GraphElement> observationSeen = new HashSet<GraphElement>();
			for(GraphElement observationNode : entry.getValue().getObservedNodes()){
				GraphElement granule = AuditUtils.getNodeGranule(observationNode, granularity);
				if(granule != null){
					observationSeen.add(granule);
				}
			}
			// the granularity level is too fine for the request granule
			if(observationSeen.isEmpty()){
				continue;
			}
			
			Date bucket = new Date(entry.getKey());
			if(timeUnit == TimeUnit.SECONDS){
				bucket = AuditUtils.trimToSecond(bucket);
			} else if(timeUnit == TimeUnit.MINUTES){
				bucket = AuditUtils.trimToMinute(bucket);
			} else if(timeUnit == TimeUnit.HOURS){
				bucket = AuditUtils.trimToHour(bucket);
			} else if(timeUnit == TimeUnit.DAYS){
				bucket = AuditUtils.trimToDay(bucket);
			}
			
			// update the number of total observations
			Long key = bucket.getTime();
			if(totalObservationsPerTimeUnit.containsKey(key)){
				Integer count = totalObservationsPerTimeUnit.remove(key);
				totalObservationsPerTimeUnit.put(key, count + 1);
			} else {
				totalObservationsPerTimeUnit.put(key, 1);
			}
			
			// update the number of unique observations
			if(!observationsSeen.contains(observationSeen)){
				// observation is unique
				observationsSeen.add(observationSeen);
				if(uniqueObservationsPerTimeUnit.containsKey(key)){
					Integer count = uniqueObservationsPerTimeUnit.remove(key);
					uniqueObservationsPerTimeUnit.put(key, count + 1);
				} else {
					uniqueObservationsPerTimeUnit.put(key, 1);
				}
			} else {
				// observation is not unique
				if(!uniqueObservationsPerTimeUnit.containsKey(key)){
					uniqueObservationsPerTimeUnit.put(key, 0);
				}
			}
		}
		
		// calculate repeats as the difference between total and unique
		HashMap<Long,Integer> repeatObservationsPerTimeUnit = new HashMap<Long,Integer>();
		for(Entry<Long,Integer> totalEntry : totalObservationsPerTimeUnit.entrySet()){
			repeatObservationsPerTimeUnit.put(totalEntry.getKey(), totalEntry.getValue() - uniqueObservationsPerTimeUnit.get(totalEntry.getKey()));
		}
		
		return createXYChart(createXYDataset(repeatObservationsPerTimeUnit));
	}
	
	private JFreeChart createXYChart(XYDataset dataset) {
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, // title
				"Time", // x-axis label
				"Observations", // y-axis label
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

	private XYDataset createXYDataset(HashMap<Long, Integer> repeatObservations) {
		TimeSeries s1 = new TimeSeries("Repeat Observations");

		switch (timeUnit) {
		case SECONDS:
			for (Entry<Long, Integer> entry : repeatObservations.entrySet()) {
				s1.add(new Second(new Date(entry.getKey())), new Double(entry.getValue()));
			}
			break;
		case MINUTES:
			for (Entry<Long, Integer> entry : repeatObservations.entrySet()) {
				s1.add(new Minute(new Date(entry.getKey())), new Double(entry.getValue()));
			}
			break;
		case HOURS:
			for (Entry<Long, Integer> entry : repeatObservations.entrySet()) {
				s1.add(new Hour(new Date(entry.getKey())), new Double(entry.getValue()));
			}
			break;
		case DAYS:
			for (Entry<Long, Integer> entry : repeatObservations.entrySet()) {
				s1.add(new Day(new Date(entry.getKey())), new Double(entry.getValue()));
			}
			break;
		default:
			break;
		}

		TimeSeriesCollection dataset = new TimeSeriesCollection();
		dataset.addSeries(s1);
		
		return dataset;
	}

}
