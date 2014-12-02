package toolbox.auditmon;

public class AuditConstants {

	// levels of granularity range in order from finest to coarsest
	public enum Granularity {
		PROGRAM_ARTIFACT,
		PARENT_CLASS, 
		SOURCE_FILE, 
		PACKAGE, 
		PROJECT;
	}
	
	public enum TimeUnit {
		SECONDS, MINUTES, HOURS, DAYS;
	}
	
}
