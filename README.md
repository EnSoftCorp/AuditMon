AuditMon
========

## Background
AuditMon is a tool for recording and analyzing human Code Exploration Trails during code audits.

For additional details see [https://ensoftcorp.github.io/AuditMon](https://ensoftcorp.github.io/AuditMon).

## Setup
### Option A) Install from Eclipse Update Site
Navigate to [https://ensoftcorp.github.io/AuditMon/install.html](https://ensoftcorp.github.io/AuditMon/install.html) and follow installation instructions.

### Option B) Installing from Source

1. Install the [Toolbox Commons](https://ensoftcorp.github.io/toolbox-commons/install.html) plugin dependency.
2. Import the `com.ensoftcorp.open.auditmon` and `org.jfree` projects.
3. Install the `com.ensoftcorp.open.auditmon` and `org.jfree` projects, by right clicking on a project and selecting `Export`.  Select `Plug-in Development`->`Deployable plug-ins and fragments`.  Make sure both the `com.ensoftcorp.open.auditmon` and `org.jfree` projects are checked and then select the `Install into host. Repository:` radio box and click `Finish`.  Press `OK` for the notice about unsigned software.  Once Eclipse restarts the plugin is installed.
4. It is advisable to close or remove the `com.ensoftcorp.open.auditmon` and `org.jfree` projects from the workspace after installation.

## Usage
AuditMon can be used programatically or directly through the Atlas shell.  To use with the Atlas Shell, import the `example.shell` project into the workspace and navigate to `Window`->`Show View`->`Other...`->`Atlas`->`Atlas Shell`.  Select the `example.shell` project and press `OK`.
