/*
 * This is an initialization file for the Atlas Interpreter view.  Each time an Interpreter linked
 * with this project is opened or restarted, the code in this file will be run as scala code.  Below
 * is included the default initialization code for the interpreter.  As long as this file exists only
 * the code in this file will be run on interpreter startup; this default code will not be run if you
 * remove it from this file.
 * 
 * You do not need to put initialization code in a scala object or class.
 */
import com.ensoftcorp.atlas.core.query.Q
import com.ensoftcorp.atlas.core.query.Attr
import com.ensoftcorp.atlas.core.query.Attr.Edge
import com.ensoftcorp.atlas.core.query.Attr.Node
import com.ensoftcorp.atlas.core.script.Common._
import com.ensoftcorp.atlas.ui.shell.lib.Common._
import com.ensoftcorp.atlas.core.db.Accuracy._
import com.ensoftcorp.atlas.core.db.graph.Graph

// color for graph highlighting
import java.awt.Color

// auditmon
import toolbox.auditmon._
import toolbox.auditmon.charts._