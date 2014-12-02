AuditMon
========

## Background
AuditMon is a tool for recording and analyzing human Code Exploration Trails during code audits.

For additional details see [AuditMon.com](http://auditmon.com/ "AuditMon.com").

## Setup
### Option A) Install from Eclipse Update Site
Navigate to [http://www.auditmon.com/install.html](http://www.auditmon.com/install.html "http://www.auditmon.com/install.html") and follow installation instructions.

### Option B) Installing from Source

  - Import the `toolbox.auditmon` and `org.jfree` projects.
  - Please use the Orbit project to satisfy common missing dependencies.
    
    You should install the following plugins from
    `http://download.eclipse.org/tools/orbit/downloads/drops/R20140525021250/repository/`
    - Apache Commons IO 2.2.0
    - Apache Commons Lang 3.1.0

Install the `org.jfree` project, by right clicking on the project and selecting `Export`.  Select `Plug-in Development`->`Deployable plug-ins and fragments`.  Select the `Install into host. Repository:` radio box and click `Finish`.  Press `OK` for the notice about unsigned software.  Once Eclipse restarts the plugin is installed and it is advisable to close or remove the `org.jfree` project from the workspace.

To install `toolbox.auditmon` repeat the `Export` steps with the `toolbox.auditmon` project.

## Usage
AuditMon can be used programatically or directly through the Atlas shell.  To use with the Atlas Shell, import the `toolbox.shell` project into the workspace and navigate to `Window`->`Show View`->`Other...`->`Atlas`->`Atlas Shell`.  Select the `toolbox.shell` project and press `OK`.
