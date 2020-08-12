

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.BreakStatement
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.tools.shell.util.Logger
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehause.groovyx.gpars.*


@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class CTAnalysisAST extends CompilationCustomizer{	
	
	List handlers //a list of handlers called from events
	List devices //a list of device variables set by the inputs
	List allDecMeths //a list of declared methods in the application
	MethodVisitor mv //a method visitor object
	Logger log //a Logger class obect to log the analysis on an external file
	
	boolean DEBUG = false
	
	//Class Constructor
	public CTAnalysisAST(){
		
		super(CompilePhase.SEMANTIC_ANALYSIS)//initialise the super CompilationCustomizer class
		
		//initialise the data structs
		handlers = new ArrayList<Handler>() //keep as arraylist for now for simplicity
		devices = new ArrayList();
		allDecMeths = new ArrayList();
			 
	}
	
	List getHandlers() {
		return handlers
	}
	
	List getDevices() {
		return devices
	}
	
	
	@Override
	void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
		
		//get all declared methods from the classNode
		allDecMeths = classNode.getAllDeclaredMethods()
		
		//create a new method visitor object
		mv = new MethodVisitor()
		classNode.visitContents(mv) //visit the contents of the classNode AST using the method visitor mv
		
		if(DEBUG) {
			println " "
			println "________CONTENTS__VISITED________"
			println "________CALL_HELPER_GETTER_______"
			println " "
			summary()
			println " "
		}
		//call the helper method to get the remaining elements from the application code
		getStateVariables(classNode)
		
		if(DEBUG)
			println "________STATE_VAR_AN_DONE________"
		//print out a summary of the data structures
		summary()
		
//		ConsistencyAnalysis conAn = new ConsistencyAnalysis(getHandlers())
//		
//		conAn.analyse()
//		
//		conAn.print()
	}
	
	//a methodCall visitor helper
	void visitMethodCall(MethodCallExpression mce) {
		mv.visitMethodCallExpression(mce)
	}

	//Caller method for getting handler arguments, state accesses, and event informations usage
	//from the handler code
	void getStateVariables(ClassNode cn) {
		
		
		//PATHLOG LEGEND:
		//c: conditional block
		//b: boolean expression
		//bl: binary leftside exp
		//br: binary rightside exp
		//so: scheduled overwrite truef
		//sf: scheduled overwrite false
		//d: + device name
		def pathLog 
		
		//cycle through all the handlers
		handlers.each { hdl->
			if(DEBUG) println "Handler Name: " + hdl.name
			
			if(hdl.devName.contains("Scheduler")) {
				pathLog = "s:"
			}
			else {
				pathLog = ""
			}
			
			//for each get the method node of the handler method from the classnode
			MethodNode methN = cn.getDeclaredMethods(hdl.name).get(0)
			 
			//get the arguments to the handler and store them in the handler structure
			methN.getParameters().each { prm->
				hdl.addArg(prm.getName())
			}
			
			//recursively analyze the method node
			handlerMNodeHelper(methN, hdl, cn, pathLog)
			
			//for each method called from the handler code, 
			//anaylze the called method if it is declared and defined in the code
			hdl.calledMethods.each { mt->
				if(DEBUG) println "called meth " + mt.method
				
				if(cn.getDeclaredMethods(mt.method).size()>0) {
					methN = cn.getDeclaredMethods(mt.method).get(0)
					handlerMNodeHelper(methN, hdl, cn, pathLog + mt.callPath)
				}
			}
		
		}
	}
	
	//Helper method to call recursive action for getting state and event information 
	void handlerMNodeHelper(MethodNode mn, Handler hdl, ClassNode cn, String pathLog) {
		
		//methodnode exists
		if(mn!=null) {
			if(DEBUG) println "MNode Name " + mn.getName()
			
			//get the code of the method node as a block statement
			BlockStatement block = (BlockStatement) mn.getCode()
		
			//cycle through the statements in the block and analyze each using the recursive method
			block.getStatements().each { st ->					
				stateRecurse(st, hdl, cn, pathLog)
			}
		}
	}
	
	//A recursive analysis method for getting state and event usage information and
	//storing it in the handler object passed as parameter
	void stateRecurse(Statement st, Handler hdl, ClassNode cn, String pth) {
		
		//if block statement cycle over the statements and call this method on each
		if(st instanceof BlockStatement) {
			st.getStatements().each { bst->
				stateRecurse(bst, hdl, cn, pth)
			}
		} //if it is an if statement call this method on the boolean expression, if block, and else block
		else if(st instanceof IfStatement) {
			def mods = ""
			Statement est = new ExpressionStatement((Expression)st.getBooleanExpression())
			stateRecurse(est, hdl, cn, pth + "b:")//boolean block
			
			if(st.getElseBlock().isEmpty()) {
				mods += "i-"
			} else {
				mods += "el-"
			}
			
			if(est.getText().contains("now") || est.getText().contains("time"))	
				mods += "t-"
			
			if(est.getText().contains("state.")) {
				mods += "s-"
			}
			
			hdl.args.each { a-> 
				if(est.getText().contains(a)) {
					String txt = est.getText()
					if(DEBUG) println "EST Txt: " + txt
					mods += "e-" + a + "."
					def i = txt.indexOf(a) + a.length() + 1
					while(i<txt.size() && !(txt.getAt(i)== " " || txt.getAt(i)== "=" || txt.getAt(i)== "!")){
						mods += txt.getAt(i)
						i++
					}
					mods += "|-"
					if(DEBUG) {
						println "Mods " + mods
						println "Mod Handler: " + hdl.name
						println "Statement: " + est
						if(hdl.name.contains("buttonEventOpen"))
							println "check"
					}
				}
			}
			
				
			Expression mex = st.getBooleanExpression().getExpression()	
			if(mex instanceof MethodCallExpression) {
				def i = hdl.getMeth(mex.getMethodAsString())
				if(hdl.calledMethods.get(i).useState) {
					mods += "s-"
				}
			}
				
			stateRecurse(st.getIfBlock(), hdl, cn, pth  + mods + "ic:")//if block
			stateRecurse(st.getElseBlock(), hdl, cn, pth  + mods + "ec:")
		}
		//if it is either an expression statement or return expression statement 
		//cast it as expression and analyze
		else if(st instanceof ExpressionStatement || st instanceof ReturnStatement) {
			Expression exp = st.getExpression()
			
			//Check for usage of event information as the event is passed as a parameter,
			//get parameters and check if the expression contains the parameter
			hdl.args.each { arg->
				if(hdl.args.size()>0 && exp.getText().contains(arg)) {
					hdl.addEvtProp(exp.getText())
					if(DEBUG) println "contain evt "+ exp.getText()
				}
			}
			
			//if exp is a methodcall expression, add the method call to handler
			//check for scheduled execution using pre-defined method names
			//check for notification sending using predefined method names
			//if the method is declared within the code, get the method node 
			//and call the recursive method node helper method to analyze
			if(exp instanceof MethodCallExpression) {
				//get the name of the method
				def mname = exp.getMethodAsString()
				//add the method to the list of called methods from the handler
				
				//Check if the method itself uses state and log that info when creating a method object
				def sta = false
				def stat = ""
				if(cn.getDeclaredMethods(exp.getMethodAsString()).size()>0) {
					MethodNode mn = cn.getDeclaredMethods(exp.getMethodAsString()).get(0)
					if(mn.getCode().getText().contains("state.")) {
						if(DEBUG) println "meth contain state"
						String code = mn.getCode().getText()
						def sin = code.lastIndexOf("state.")
						sin += 6
						stat = "st:"
						while(code.getAt(sin)!=" ") {
							stat += code.getAt(sin)
							sin++
						}
						stat += ":"
						if(DEBUG) println "Meth State use " + stat + " " + hdl.name
						sta = true
					}
				}
				
				//get the receiver of the method call
				def rec = exp.getReceiver().toString()
				if(exp.getReceiver() instanceof VariableExpression)
					rec = exp.getReceiver().getName()
					
				//create and add the method call to the handler
				boolean dev = hdl.devMethHelper(rec, devices)
				hdl.addMethodCall(exp, pth + stat, sta, dev)
				
				if(exp.getText().toLowerCase().contains("timeofday") || exp.getText().contains("now")) {
					if(DEBUG) println "Time: " + exp.getText()
					hdl.addTimAcc(exp.getText())
				}
				
				
				String mtext = exp.getMethodAsString().toLowerCase()
				//check for scheduling that uses predefined methodcalls
				if(mtext.contains("runin") || (mtext.contains("schedule") && !mtext.contains("unschedule") )
					|| mtext.contains("runonce") || mtext.contains("runevery")) {
					//get the argument that contains the name of the method scheduled for execution
					String schMeth = ""
					if(DEBUG) println "Schedule Exp " + exp
					if(mtext.contains("runevery")) {
						schMeth = exp.getArguments().getAt(0).getText()
					} else {
						schMeth = exp.getArguments().getAt(1).getText()
					}
					
					//find the declared method within the code and get the method node
					def mn = cn.getDeclaredMethods(schMeth).get(0)
					
					//create a new methodcallexpression statement using the schedule as the receiver,
					//the name of the method scheduled as the method name
					//and the parameters of that method node converted to an argument list expression as arguments
					ExpressionStatement ext = new ExpressionStatement(
						new MethodCallExpression(
							new VariableExpression("this"), 
							schMeth, 
							new ArgumentListExpression(mn.getParameters())))
					
					if(DEBUG) println "Path sch: " + pth
					if(hdl.schOverWrite) {
						stateRecurse(ext, hdl, cn, pth + "so:")
					} else {
						stateRecurse(ext, hdl, cn, pth + "sf:")
					}
				}
				
				//check for notification/sms sending functions, set the flag for message usage on the handler
				if(exp.getText().contains("sendSms") || exp.getText().contains("sendPush")
					|| exp.getText().contains("sendNotificationToContacts")) {
					hdl.setMsg(true)
				}
				
				//if the method declaration is in the code, call the handler helper on the method node
				if(cn.getDeclaredMethods(mname).size()>0) {
					handlerMNodeHelper(cn.getDeclaredMethods(mname).get(0), hdl, cn, pth)
				}
				//-------------------------------------------------------------------------------------------------WIP-----------
				//find a way to log device accesses
				//Dealing with findAll and each methodcalls
				//Goes into inside of them to check for device related calls
				else if(exp.getArguments().getAt(0) instanceof ClosureExpression) {
					//get the receiver of the call to foreach
					def recver = exp.getReceiver().getText()
					def isDev = false //a flag for whether the receiver is a device or not
					if(DEBUG) println "Path Closure: " + pth
					
					//cycle through the devices to see if it contains the receiver,
					//if it contains then set isDev to true
					devices.each { dv->
						if(dv.devName.contains(recver)) {
							isDev = true
						}
					}
					
					def parName = ""
					if(DEBUG) println "Closure: MCE " + exp
					ClosureExpression ce = exp.getArguments().getAt(0)
					
					if(DEBUG) println "Closure : " + ce
					BlockStatement bst = (BlockStatement) ce.getCode()
					//if it is a device that is being accessed, get the stand-in variable for the device
					//pass the name of the parameter into the path log string
					if(isDev) {
						//if uses parameter -> {...} format	
						if(DEBUG) println "Path Param: " + pth
						
						bst.getStatements().each { bs->
							if(DEBUG) println "Recurse the Block in Closure"
							if(DEBUG) println "Path rec: " + pth
							stateRecurse(bs, hdl, cn, pth + "d:" + recver + ":")
						}
						
					}
					else {
						bst.getStatements().each { bs->
							if(DEBUG) println "Recurse the Block in Closure"
							stateRecurse(bs, hdl, cn, pth)
							
						}
					}
					
				}
			}
			//if the expression is a binary expression
			else if(exp instanceof BinaryExpression) {
				
				Expression lex = exp.getLeftExpression()
				Expression rex = exp.getRightExpression()
				
				//if the left side contains state, then it is an assignment to the state variable
				//add it as a write to state
				if(lex.text.contains("state.") && !pth.contains("br:")) {
					
					//if the binary op is a checking operation with state on the left side add to read states
					if(exp.getOperation().getText().contains("==") || exp.getOperation().getText().contains("<")
						|| exp.getOperation().getText().contains(">") || exp.getOperation().getText().contains("!")) {
						
						hdl.addReadState(exp.getLeftExpression().getText(), pth + "bl:", exp.getRightExpression())
					}
					else {
						//check for the expression type of the assigned value
						//cont = constant value
						//var = variable value
						def assignType = exp.getRightExpression() instanceof ConstantExpression ? "cont" : "var"
						hdl.addWriteState(exp.getLeftExpression().getText(), pth + "bl:" + assignType, exp.getRightExpression())
					}
				}
				
				
				
				//if state is on the right, then it is a read on state
				if(rex.text.contains("state.")) {
					if(exp.getOperation().getText().contains("==") || exp.getOperation().getText().contains("<")
						|| exp.getOperation().getText().contains(">") || exp.getOperation().getText().contains("!")) {
						hdl.addReadState(exp.getRightExpression().getText(), pth + "br:", exp.getLeftExpression())
					}else {
						hdl.addReadState(exp.getRightExpression().getText(), pth + "br:")
					}
				}
				
				//recurse analyze right epxression	
				ExpressionStatement est = new ExpressionStatement(rex)
				stateRecurse(est, hdl, cn, pth + "br:")
				
				//recurse analyze left expression
				est = new ExpressionStatement(lex)
				stateRecurse(est, hdl, cn, pth + "bl:")
			}
			//if boolean expression, 
			else if(exp instanceof BooleanExpression) {
				//get the expression
				Expression xp = exp.getExpression()
				
				//if it is a methodcallexpression
				if(xp instanceof MethodCallExpression) {
					def mname = xp.getMethodAsString()
					if(cn.getDeclaredMethods(mname).size()>0)
						handlerMNodeHelper(cn.getDeclaredMethods(mname).get(0), hdl, cn, pth + "b:")
				}
				stateRecurse(new ExpressionStatement(xp), hdl, cn, pth + "b:")
			}
			else if(exp instanceof ConstructorCallExpression) {
				if(exp.getText().contains("Date")) {
					if(DEBUG) println "Date: " + exp
					hdl.addTimAcc(exp.getText())
				}
			}
			else if(exp instanceof PropertyExpression) {
				if(exp.getText().contains("state.") && !pth.contains("bl:")) {
					hdl.addReadState(exp.getText(), pth)
				}
			}
		}
		
	}
		
	class MethodVisitor extends ClassCodeVisitorSupport{
		
		
		public MethodVisitor( ) {
		}
		
		@Override
		void visitIfElse(IfStatement ifs) {
			if(DEBUG) println "In Ifelse visitor: " + ifs.toString()
			if(ifs.getIfBlock() instanceof ExpressionStatement) {
				Expression exp = ifs.getIfBlock().getExpression()
				if(exp instanceof MethodCallExpression) {
					if(DEBUG) println "Methodcall exp: " + exp.getMethodAsString()
					visitMethodCallExpression(exp)
				}
			}else {
				BlockStatement bs = (BlockStatement) ifs.getIfBlock()
				bs.getStatements().each { st->
					if(st instanceof ExpressionStatement) {
						Expression exp = st.getExpression()
						if(exp instanceof MethodCallExpression) {
							visitMethodCallExpression(exp)
						}
					}
				}
			}
			if(!ifs.getElseBlock() instanceof EmptyStatement) {
				if(ifs.getIfBlock() instanceof ExpressionStatement) {
					Expression exp = ifs.getIfBlock().getExpression()
					if(exp instanceof MethodCallExpression) {
						visitMethodCallExpression(exp)
					}
				}else {
					bs = (BlockStatement) ifs.getElseBlock()
					bs.getStatements().each { st->
						if(st instanceof ExpressionStatement) {
							Expression exp = st.getExpression()
							if(exp instanceof MethodCallExpression) {
								visitMethodCallExpression(exp)
							}
						}
					}
				}
			}
		}
		
		
		//Visitor for MEthod Call Expressions
		//
		@Override
		public void visitMethodCallExpression(MethodCallExpression mce) {
						
			def mceText
			
			//Store the name of the method called as a string
			if(mce.getMethodAsString() == null)
			{
				mceText = mce.getText()
			}else
				mceText = mce.getMethodAsString()
				
			if(DEBUG) println "Visit MCE: " + mceText
			
			//INPUT METHOD HANDLER
			if(mceText.equals("input")) {

				//if the method has more than zero arguments
				if(mce.getArguments().toList().size() > 0) {
					
					//get the list of arguments
					def args = mce.getArguments()
					
					def dname //device name assoc. w/ the handler
					List dcap = new ArrayList() //device capabilities requested
					def isDevice = false //if the input is a device input or a value/mode input
					
					//for each argument for the input method call
					args.each { arg->
						
						//if argument is a constant expression
						//if it doesn't contain capabilty request and not a map expression, is the name of the input/device variable
						//if it contains capabilty request, then input is a device, store the requested cap in the cap list
						//if input is a device, then create a new device object and add to devices list
						if(arg instanceof ConstantExpression) {
							if(!arg.getText().contains("capability.")) {
								//print "Device Name: "
								dname = arg.getValue()
							}else {
//								print "Device Cap: " 
								isDevice = true
								dcap.add(arg.getValue())
							}							
						} 
					}
					if(isDevice)
						devices.add(new Device(dname, dcap))	
				}
			}
			
			//SUBSCRIPTION HANDLER
			if(mceText.equals("subscribe")) {
				
				List arglist = mce.getArguments().toList()
				
				if(DEBUG) println "Handler Subs: " + mce.getText()
				
				def hname
				def dname
				def ename
				
				if(arglist.get(0) instanceof VariableExpression){
					
					VariableExpression varex = (VariableExpression) arglist.get(0)
					dname = varex.getName()
					if(DEBUG) println "Dev Name: "
				
				}
				
				if(arglist.get(1) instanceof ConstantExpression){
					
					ConstantExpression conex = (ConstantExpression) arglist.get(1)
					
					ename = conex.getValue()
				}
				
				if(arglist.get(2) instanceof VariableExpression){
					VariableExpression varex = (VariableExpression) arglist.get(2)
					
					hname = varex.getName()
				} 
				else if(arglist.get(2) instanceof ConstantExpression) {
					ConstantExpression conex = (ConstantExpression) arglist.get(2)
					
					hname = conex.getValue()
				}
				
				if(DEBUG) println "Handler & Device: " + hname + " " + dname + " " + ename
				
				handlerAdder(new Handler(hname, dname, dname + "." + ename))
				
			}
			
			if(mceText.equals("schedule")) {
				if(DEBUG) println "Schedule: " + mce.getArguments()
				List arglist = mce.getArguments().toList()
				
				def hname = ((ConstantExpression) arglist.get(1)).getValue()
				def dname = "Scheduler"
				def ename = "Schedule on " + ((VariableExpression)arglist.get(0)).getName()
				boolean schOvrWr = true 
				
				//get the arguments, if there are more than 2 arguments that means there's an explicit
				//setting of overwrite or other properties
				//check the map of modified parameters for overwrite
				//if it is set to false, update the schOvrWr boolean to false
				if(arglist.size() > 2) {
					MapExpression mep = arglist.get(2)
					mep.getMapEntryExpressions().each { mee->
						if(mee.getKeyExpression().getText().contains("overwrite")) {
							if(mee.getValueExpression().getText().contains("false"))
								schOvrWr = false
						}
					}
					
				}
				
				Handler h = new Handler(hname, dname, ename)
				
				h.setSch(schOvrWr)
				
				handlers.add(h)
				
			}
			
			//recurse on the method expression to get the inner methods	
			super.visitMethodCallExpression(mce)
		}
		
		@Override
		protected SourceUnit getSourceUnit() {
			return null;
		}
	}
	
	//Printer Method
	public void summary() {
		
		println "-------------------------------------------------------"
		
		println "Devices:"
		devices.each{ dev->
			println(dev)
		}
		
		println "-------------------------------------------------------"
		
		println "Handlers: "
		handlers.forEach { hand ->
			println(hand)
		}
		println "-------------------------------------------------------"
		
	}
	
	class Device{
		
		//Device Name
		String devName
		//Capabilty List
		List cap
		
		public Device(String n, List c) {
			devName = n
			cap = new ArrayList()
			cap = c
		}
		
		@Override
		public String toString() {
			return "Device Name: " + devName + " Capability Req: " + cap + ""
		}
	}
	
	public void handlerAdder(Handler hdl) {
		
		if(handlers.contains(hdl)) {
			handlers.get(handlers.indexOf(hdl)).addEvent(hdl.eventTriggers.get(0))
		}else {
			handlers.add(hdl)
		}
		
	}
	
}