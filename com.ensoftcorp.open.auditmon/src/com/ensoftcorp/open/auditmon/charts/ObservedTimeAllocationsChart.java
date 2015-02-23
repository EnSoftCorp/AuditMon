package com.ensoftcorp.open.auditmon.charts;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.open.auditmon.AuditConstants.Granularity;
import com.ensoftcorp.open.auditmon.AuditConstants.TimeUnit;
import com.ensoftcorp.open.auditmon.AuditUtils;
import com.ensoftcorp.open.auditmon.AuditUtils.AbstractObservation;
import com.ensoftcorp.open.auditmon.AuditUtils.ObservationType;

public class ObservedTimeAllocationsChart extends AuditChart {

	private TimeUnit timeUnit;
	private Granularity granularity;
	
	public ObservedTimeAllocationsChart(String session, TimeUnit timeUnit, Granularity granularity) {
		super(session, "");
		this.timeUnit = timeUnit;
		this.granularity = granularity;
		this.showLegend = false;
		this.showLabels = false;
		
		if(timeUnit == TimeUnit.SECONDS){
			title += " Seconds Observing ";
		} else if(timeUnit == TimeUnit.MINUTES){
			title += " Minutes Observing ";
		} else if(timeUnit == TimeUnit.HOURS){
			title += " Hours Observing ";
		} else if(timeUnit == TimeUnit.DAYS){
			title += " Days Observing ";
		}
		
		if(granularity == Granularity.PROGRAM_ARTIFACT){
			title += "Program Artifact";
		} else if(granularity == Granularity.PARENT_CLASS){
			title += "Class";
		} else if(granularity == Granularity.SOURCE_FILE){
			title += "Source File";
		} else if(granularity == Granularity.PACKAGE){
			title += "Package";
		} else if(granularity == Granularity.PROJECT){
			title += "Project";
		} 
	}

	@Override
	public JFreeChart getChart() {
		TreeMap<Long, AbstractObservation> observations = AuditUtils.getSessionObservations(session);
		HashMap<GraphElement,Long> timeAllocations = new HashMap<GraphElement,Long>();
		// iterate over the observations in order and add up the time deltas 
		// according to the granularity level
		AbstractObservation lastObservation = null;
		for (Entry<Long, AbstractObservation> entry : observations.entrySet()) {
			
			// start and end nodes don't count towards time spent, but they do reset the time deltas
			if(entry.getValue().getType() == ObservationType.START || entry.getValue().getType() == ObservationType.STOP){
				lastObservation = null;
				continue;
			}
			
			if(lastObservation != null){
				long timeDelta = entry.getValue().getTimestamp() - lastObservation.getTimestamp();
				for(GraphElement programArtifact : lastObservation.getObservedNodes()){
					GraphElement granule = AuditUtils.getNodeGranule(programArtifact, granularity);
					if(granule != null){
						if(timeAllocations.containsKey(granule)){
							Long timeSpent = timeAllocations.remove(granule);
							timeAllocations.put(granule, timeSpent + timeDelta);
						} else {
							timeAllocations.put(granule, timeDelta);
						}
					}
				}
			}
			
			// update last time for next round
			lastObservation = entry.getValue();
		}
		
		// convert timeAllocations to a displayable version of the data
		HashMap<String,Long> timeAllocationsDisplay = new HashMap<String,Long>();
		for(Entry<GraphElement,Long> timeAllocation : timeAllocations.entrySet()){
			timeAllocationsDisplay.put(AuditUtils.getNodeGranuleDisplayName(timeAllocation.getKey(), granularity), timeAllocation.getValue());
		}
		
		return createPieChart(createPieDataset(timeAllocationsDisplay));
	}
	
	private DefaultPieDataset createPieDataset(final HashMap<String, Long> observedElements) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        if(observedElements != null){
            for(Entry<String,Long> entry : observedElements.entrySet()){
                double formattedTime = 0.0;
                if(timeUnit == TimeUnit.SECONDS){
                	formattedTime = entry.getValue() / 1000.0;
                } else if(timeUnit == TimeUnit.MINUTES){
                	formattedTime = entry.getValue() / 1000.0 / 60.0;
                } else if(timeUnit == TimeUnit.HOURS){
                	formattedTime = entry.getValue() / 1000.0 / 60.0 / 60.0;
                } else if(timeUnit == TimeUnit.DAYS){
                	formattedTime = entry.getValue() / 1000.0 / 60.0 / 60.0 / 24.0;
                }
                dataset.setValue(entry.getKey() + " ", new Double(formattedTime));
            }
        }
        return dataset;        
    }
    
    private JFreeChart createPieChart(PieDataset dataset) {
        JFreeChart chart = ChartFactory.createPieChart(
            title, // chart title
            dataset, // chart data
            showLegend, // include legend
            true, // tooltips
            false // urls
        );

        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setSectionOutlinesVisible(false);
        plot.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        plot.setNoDataMessage("No data available");
        plot.setCircular(false);
        plot.setLabelGap(0.02);
        
        if(!showLabels){
                plot.setLabelGenerator(null);
        }
        
        return chart;
    }

}
