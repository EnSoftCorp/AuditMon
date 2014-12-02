package toolbox.auditmon;

import java.util.Calendar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class DateTimePicker {

	protected Shell shell;

	private Calendar result = Calendar.getInstance();
	
	public static Long getDateTime(){
		DateTimePicker picker = new DateTimePicker();
		return picker.open();
	}
	
	/**
	 * This is a dummy method for window builder to parse and still be useful,
	 * don't use! You will get thread access issues...
	 * 
	 * @wbp.parser.entryPoint
	 */
	@SuppressWarnings("unused")
	private void display() {
		Display display = Display.getDefault();
		createContents();
		shell.open();
		shell.layout();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
	
	/**
	 * Create the dialog.
	 * @wbp.parser.entryPoint
	 */
	private DateTimePicker() {
	}

	/**
	 * Open the dialog.
	 * @return the result
	 * @wbp.parser.entryPoint
	 */
	public Long open() {
		final Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			public void run() {
				createContents();
				shell.open();
				shell.layout();
				while (!shell.isDisposed()) {
					if (!display.readAndDispatch()) {
						display.sleep();
					}
				}
			}
		});

		return result.getTimeInMillis();
	}

	/**
	 * Create contents of the dialog.
	 * @wbp.parser.entryPoint
	 */
	private void createContents() {
		shell = new Shell();
		shell.setSize(234, 256);
		shell.setLayout(new GridLayout(1, false));
		
		final DateTime calendar = new DateTime(shell, SWT.CALENDAR);
		calendar.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 1, 1));
		
		final DateTime time = new DateTime(shell, SWT.TIME);
		time.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		shell.setText("Date Time Picker");
		
		calendar.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				result.set(Calendar.DAY_OF_MONTH, calendar.getDay());
				result.set(Calendar.MONTH, calendar.getMonth());
				result.set(Calendar.YEAR, calendar.getYear());
			}
		});

		time.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				result.set(Calendar.HOUR_OF_DAY, time.getHours());
				result.set(Calendar.MINUTE, time.getMinutes());
				result.set(Calendar.SECOND, time.getSeconds());
			}
		});
	}

}
