import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement

class Handler{
	
	String name
	String devName
	
	boolean hasMsg
	
	List args
	List eventTriggers
	List readStates
	List writeStates
	List calledMethods
	List eventProps //what info of the event is used, if used
	
	
	public Handler(String n, String dn, String en) {
		name = n
		devName = dn
		hasMsg = false
		
		eventTriggers = new ArrayList<String>()
		
		eventTriggers.add(en)
		
		args = new ArrayList<String>()
		
		calledMethods = new ArrayList<Method>()
		eventProps = new ArrayList()
		readStates = new ArrayList()
		writeStates = new ArrayList()
	}
	
	@Override
	boolean equals(Object o) {
		if(o instanceof Handler) {
			return this.name == o.name
		}
	}
	
	void setMsg(boolean b) {
		hasMsg = b
	}
	
	//Adds the arg string parameter of the handler call to the arguments list
	void addArg(String arg) {
		args.add(arg)
	}
	
	//Get an expression as string, finds the last index of the argument passed to the handler contained in the string
	//while there are still unprocessed presences of the handler parameter accesses,
	//walks through the string beginning at the last index of the parameter name and adds the chars to a string
	//until it reads a space, =, !, ?, or a closing parenthesis without an opening parenthesis immediately before it
	//adds the constructed string to the list of event properties accessed arraylist if it's not already present
	void addEvtProp(String prop) {
		if(!eventProps.contains(prop)) {
			def evt = ""
			
			def k = prop.length()
			
			args.each { arg->
			
				while((k = prop.lastIndexOf(arg, k-1))!= -1) {
					def i = k
					evt = ""
					while(i < prop.size() && (prop.getAt(i)!= " " &&
						((prop.getAt(i-1) != "(")? prop.getAt(i)!= ")":true) && prop.getAt(i)!= "=" && prop.getAt(i)!= "!"
						&& prop.getAt(i)!= "?")) {
					
						evt = evt + prop.getAt(i)
				//		println "Event Name: " + i +" "+ k + evt
						i++
					}
					if(!eventProps.contains(evt))
						eventProps.add(evt)
				}
				
			}
		}
	}
	
	void addEvent(String evt) {
		eventTriggers.add(evt)
	}
	
	void addReadState(String s) {
			readStates.add(s)
	}
	
	void addWriteState(String s) {
		if(!writeStates.contains(s))
			writeStates.add(s)
	}
	
	void addMethodCall(MethodCallExpression mexp) {
		addMethodCall(mexp, mexp.getReceiver().getText())
	}
	
	void addMethodCall(MethodCallExpression mexp, String receiver) {
		String mName = mexp.getReceiver().getText() + "." + mexp.getMethodAsString()
		Method m = new Method(receiver, mexp.getMethodAsString())
	
		//.each{} call handling for method registration
		if(mexp.getText().contains("each")) {
	//		println "For each loop Meth Expression:\n" + mexp
			mexp.getArguments().each { arg->
				if(arg instanceof ClosureExpression) {
					if(arg.getCode() instanceof BlockStatement) {
						BlockStatement bl = arg.getCode()
		//				println "Block statements: " + bl.getStatements()
						bl.getStatements().each { st->
			//				println "Statement " + st
							if(st instanceof ExpressionStatement) {
								Expression xp = st.getExpression()
				//				println "Expression: " + xp
				//				println "Method arguments: " + m.arguments
								if(xp instanceof MethodCallExpression) {
									m.addArg(xp)
								//	println "Expression: " + xp.getMethodAsString() + "\nmName: " + mName
				//					println "MCE"
									addMethodCall(xp, receiver)
								}
							}
						}
					}
				}
			}
		}
		else if(!mName.contains("log")){
			m.addArg(mexp.getArguments())
		}
		if(!calledMethods.contains(m)) {
			calledMethods.add(m)
		}
	}
	
	@Override
	public String toString() {
		def state = ""
		def methods = ""
		def triggers = ""
		def evprops = ""
		def nm = "Handler Name: " + name
		nm = nm + "("
		if(args.size()>0) {
			args.each { arg->
				nm = nm + arg
			}
		}
		nm = nm + ")"
		if(eventProps.size()>0) {
			evprops = "\nEvent Info Used: "
			eventProps.each { p->
				evprops = evprops + p + "; "
			}
		}
		if(calledMethods.size()>0) {
			methods = "\nCalled Methods: "
			calledMethods.each { m->
				methods = methods + m + "; "
			}
		}
		if(readStates.size()>0) {
			state = "\nRead States: "
			readStates.each { st->
				state = state + st + "; "
			}
		}
		if(writeStates.size()>0) {
			state = state + "\nWrite States: "
			writeStates.each { st->
				state = state + st + "; "
			}
		}
		if(eventTriggers.size()>0) {
			triggers = "\nEvent Triggers: "
			eventTriggers.each { tr->
				triggers = triggers + tr + "; "
			}
		}
		
		return nm + "\nDevice Name: " + devName + triggers + evprops + methods + state + "\nSend Notification/Msg: " + hasMsg + "\n"
	}
	
	class Method{
		
		String receiver
		String method
		List arguments
		
		public Method(String r) {
			this.Method(r, "")
		}
		
		public Method(String r, String m) {
			receiver = r
			arguments = new ArrayList()
			method = m
		}
		
		void addMethod(String m) {
			method = m
		}
		
		void addArg(Expression argexp) {
			if(argexp instanceof ArgumentListExpression) {
				argexp.each { xp->
					arguments.add(xp)
				}
			}else {
				arguments.add(argexp)
			}
		}
		
		@Override
		boolean equals(Object o) {
			if(o instanceof Method) {
				return (this.receiver.equals(o.receiver) && this.method.equals(o.method))
			}
			else
				false
		}
		
		@Override
		public String toString() {
			String st = receiver + "." + method + "("
			arguments.each { exp->
				st = st + exp.getText()
				if(arguments.size()-arguments.indexOf(exp)-1 > 0) {
					st = st + ", "
				}
			}
			st = st + ")"
			return st
		}
		
	}
}