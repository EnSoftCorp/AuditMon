package com.ensoftcorp.open.auditmon;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Scanner;

import org.eclipse.core.resources.ResourcesPlugin;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.open.auditmon.AuditUtils.AbstractObservation;
import com.ensoftcorp.open.auditmon.AuditUtils.ObservationType;
import com.ensoftcorp.open.toolbox.commons.FormattedSourceCorrespondence;

public class ObservationTimeHeatMap {

	public static void overlayHeatMapOnSource(String projectName, String session, File outputDirectory) throws Exception {
		// first figure out how much time is spent of each observed node in the index for the session
		HashMap<GraphElement,Long> timeSpentOnObservedNodes = new HashMap<GraphElement,Long>();
		AbstractObservation lastObservation = null;
		for(AbstractObservation observation : AuditUtils.getSessionObservations(session).values()){
			
			if(observation.getType() == ObservationType.START || observation.getType() == ObservationType.STOP){
				lastObservation = null;
				continue;
			}
			
			// this is the first observation, no time has been spent on it yet
			if(lastObservation == null){
				lastObservation = observation;
				continue;
			} else {
				Long timeDelta = observation.getTimestamp() - lastObservation.getTimestamp();
				AtlasSet<GraphElement> observedNodes = observation.getObservedNodes();
				// if there are more than one observed nodes, we just split the time across each node
				// if time does not split evenly a few milliseconds just won't be accounted for
				Long timeSplit = timeDelta / observedNodes.size();
				for(GraphElement observedNode : observedNodes){
					if(timeSpentOnObservedNodes.containsKey(observedNode)){
						Long previousTime = timeSpentOnObservedNodes.remove(observedNode);
						timeSpentOnObservedNodes.put(observedNode, previousTime + timeSplit);
					} else {
						timeSpentOnObservedNodes.put(observedNode, timeSplit);
					}
				}
			}
		}
		
		// now redivide out the time based on file and line number via source correpondences
		HashMap<File, Long> fileSelectionTimes = new HashMap<File, Long>();
		HashMap<FileLine, Long> fileLineSelectionTimes = new HashMap<FileLine, Long>();
		for(Entry<GraphElement, Long> entry : timeSpentOnObservedNodes.entrySet()){
			FormattedSourceCorrespondence sc = FormattedSourceCorrespondence.getSourceCorrespondent(entry.getKey());
			if(sc != null && sc.getFile() != null){
				if(!fileSelectionTimes.containsKey(sc.getFile())){
					fileSelectionTimes.put(sc.getFile(), entry.getValue());
				} else {
					Long timeObserved = fileSelectionTimes.remove(sc.getFile()) + entry.getValue();
					fileSelectionTimes.put(sc.getFile(), timeObserved);
				}
				for(long i=sc.getStartLineNumber(); i<=sc.getEndLineNumber(); i++){
					FileLine line = new FileLine(sc.getFile(), i);
					if(!fileLineSelectionTimes.containsKey(line)){
						fileLineSelectionTimes.put(line, entry.getValue());
					} else {
						Long timeObserved = fileLineSelectionTimes.remove(line) + entry.getValue();
						fileLineSelectionTimes.put(line, timeObserved);
					}
				}
			}
		}

		// iterate over each .java file in the project and output the line selection time data for each line
		File project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).getLocation().toFile();

		// add the index and css pages to the output directory
		outputDirectory.mkdirs();
		
		File css = writeCSSFile(outputDirectory);
		
		FileWriter index = new FileWriter(new File(outputDirectory.getAbsolutePath() + File.separatorChar + "index.html"));
		writeIndexHeader(index);
		generateHeatMappedFiles(project, projectName, project, outputDirectory, index, fileSelectionTimes, fileLineSelectionTimes, css);
		writeIndexFooter(index);
		index.close();
	}

	private static void writeIndexHeader(FileWriter index) throws IOException {
		index.write("<!DOCTYPE html>");
		index.write("<html>");
		index.write("<table border=\"1\">");
		index.write("<tr>");
		index.write("<th>Time Observed (milliseconds)</th>");
		index.write("<th>Class</th>");
		index.write("</tr>");
	}
	
	private static void writeIndexFooter(FileWriter index) throws IOException {
		index.write("</table>");
		index.write("</body>");
		index.write("</html>");
	}

	private static File writeCSSFile(File outputDirectory) throws IOException {
		File cssPath = new File(outputDirectory.getAbsolutePath() + File.separatorChar + "style.css");
		FileWriter css = new FileWriter(cssPath);
		css.write("article, aside, figure, footer, header, hgroup,\n");
		css.write("menu, nav, section { display: block; }\n");
		css.write("pre { margin: 0; }\n");
		css.write("pre.line-numbers {\n");
		css.write("  float: left;\n");
		css.write("  padding-right:2px;\n");
		css.write("  border-right: solid 1px black;\n");
		css.write("  margin-right:7px;\n");
		css.write("}\n");
		css.write("div.codebox {\n");
		css.write("  border: solid 2px navy;\n");
		css.write("  padding:2px;\n");
		css.write("  background-color: white;\n");
		css.write("}\n");
		css.write("div.line {\n");
		css.write("  padding: 0px;\n");
		css.write("  margin: 0px;\n");
		css.write("  display: inline;\n");
		css.write("}\n");
		css.write("div.data-time {\n");
		css.write("  display: none;\n");
		css.write("}\n");
		css.write("div.data-percent-relative-file {\n");
		css.write("  display: none;\n");
		css.write("}\n");
		css.write("div.line:hover {\n");
		css.write("  background-color:#FFEBCD;\n");
		css.write("  cursor:pointer;\n");
		css.write("}\n");
		for(int i=0;i<=100;i++){
			css.write("div.red-" + i + " {\n");
			css.write("  background-color: rgba(255,0,0," + (new Double(i)/100.0) + ");\n");
			css.write("}\n");
		}
		
		
		css.close();
		return cssPath;
	}
	
	private static void generateHeatMappedFiles(File file, String projectName, File project, File outputDirectory, 
												FileWriter index, HashMap<File, Long> fileSelectionTimes, 
												HashMap<FileLine, Long> fileLineSelectionTimes, File css) throws Exception {
	    if(!file.isDirectory() && file.getName().endsWith("java")){
	    	 File outputFile = new File(outputDirectory.getAbsolutePath() + File.separatorChar
	    			 + projectName + File.separatorChar 
	    			 + file.getAbsolutePath().substring(project.getAbsolutePath().length()+1) + ".html");
	    	 generateHeatMappedFile(file, fileSelectionTimes, fileLineSelectionTimes, outputFile, css);
	    	 String relativePath = projectName + "/" + file.getAbsolutePath().substring(project.getAbsolutePath().length()+1).replaceAll("" + File.separatorChar, "/") + ".html";
	    	 Long fileSelectionTime = fileSelectionTimes.get(file);
	    	 index.write("<tr><td>" + (fileSelectionTime == null ? "0" : fileSelectionTime.toString()) + "</td><td><a href=\"./" + relativePath + "\">" + relativePath + "</a></td></tr>\n");
	    } else {
	    	if(file.isDirectory()){
	    		File[] children = file.listFiles();
			    for (File child : children) {
			    	generateHeatMappedFiles(child, projectName, project, outputDirectory, index, fileSelectionTimes, fileLineSelectionTimes, css);
			    }
	    	}
	    }
	}
	
	private static void generateHeatMappedFile(File file, HashMap<File, Long> fileSelectionTimes, 
											   HashMap<FileLine, Long> fileLineSelectionTimes, 
											   File outputFile, File css) throws Exception {
		outputFile.getParentFile().mkdirs();
		FileWriter fw = new FileWriter(outputFile);
		Scanner scanner = new Scanner(file);
		Integer lineNumber = 1;
		fw.write("<meta charset=utf-8 />\n");
		fw.write("<title>" + file.getName() + "</title>\n");
		fw.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"" + css.getAbsolutePath() + "\">\n");
		fw.write("</head>\n");
		fw.write("<body>\n");
		fw.write("  <div class=\"codebox\">\n");
		fw.write("    <pre class=\"line-numbers\">");
		while (scanner.hasNextLine()) {
			scanner.nextLine();
			fw.write(lineNumber + "\n");
			lineNumber++;
		}
		fw.write("    </pre>");
		fw.write("    <pre class=\"source\">");
		scanner.close();
		scanner = new Scanner(file);
		lineNumber=1;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			FileLine fl = new FileLine(file, lineNumber);
			if(fileSelectionTimes.containsKey(file) && fileLineSelectionTimes.containsKey(fl)){
				long timeObservedOnFile = fileSelectionTimes.get(file);
				long timeObservedOnLine = fileLineSelectionTimes.get(fl);
				DecimalFormat df = new DecimalFormat("#.#####");
				fw.write("<div class=\"line red-" + getHeatDensityForLineOfFile(timeObservedOnFile, timeObservedOnLine) + "\">" + line +
						"<div class=\"data-time\">" + fileLineSelectionTimes.get(fl) + "</div>" + 
						"<div class=\"data-percent-relative-file\">" + df.format((timeObservedOnLine * 1.0) / (timeObservedOnFile * 1.0) * 100.0) + "</div>" + 
						"</div>\n");
			} else {
				fw.write("<div class=\"line red-0\">" + line + "</div>\n");
			}
			
			lineNumber++;
		}
		scanner.close();
		fw.write("    </pre>");
		fw.write("  </div>");
		fw.write("</body>");
		fw.write("</html>");
		fw.close();
	}
	
	public static int getHeatDensityForLineOfFile(long timeObservedOnFile, long timeObservedOnLine){
		double percentage = (timeObservedOnLine * 1.0) / (timeObservedOnFile * 1.0);
		int result = (int) Math.round(percentage * 100.0);
		return result > 100 ? 100 : result;
	}

	private static class FileLine {
		private File file;
		private long lineNumber;
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((file == null) ? 0 : file.hashCode());
			result = prime * result + (int) (lineNumber ^ (lineNumber >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FileLine other = (FileLine) obj;
			if (file == null) {
				if (other.file != null)
					return false;
			} else if (!file.equals(other.file))
				return false;
			if (lineNumber != other.lineNumber)
				return false;
			return true;
		}
		
		public FileLine(File file, long lineNumber){
			this.file = file;
			this.lineNumber = lineNumber;
		}
		
		@SuppressWarnings("unused")
		public File getFile(){
			return file;
		}
		
		@SuppressWarnings("unused")
		public long getLineNumber(){
			return lineNumber;
		}
		
		@Override
		public String toString(){
			return lineNumber + "@" + file.toString();
		}
	}

}
