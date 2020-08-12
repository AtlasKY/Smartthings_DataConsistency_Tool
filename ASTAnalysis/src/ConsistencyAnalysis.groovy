
import Handler.State
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration

class ConsistencyAnalysis {
	
	List handlers
	
	List results
	
	List unsafeResults
	
	boolean DEBUG = false
	boolean LONG_OUT = false
	
	//enum for the state result information passing and recording
	enum StateRes{
		NO_STATE,
		SAFE_READ,
		SAFE_WRITE,
		SAFE_RW,
		UNSAFE_R,
		UNSAFE_W,
		UNSAFE_RW
	}
	
	//an enum for the scheduling check for state accesses
	enum Schedule{
		OW_TRUE,
		OW_FALSE,
		NO_SCH
	}
	
	//Enum for the state write flag checks in writeCount() method
	enum StateWFlag{
		SINGLE,
		MULTI_W_SAME_VAL,
		MULTI_W_DIFF_VAL
	}
	
	public ConsistencyAnalysis(List hdls) {
		
		handlers = new ArrayList()
		handlers = hdls
		
		results = new ArrayList<AnalysisResult>()
		unsafeResults = new ArrayList<AnalysisResult>()
		
	}
	
	void analyse() {
		
		println "Analysis"
		
		for(int i = 0; i < handlers.size(); i++) {
			
			for(int j = i; j < handlers.size(); j++) {
				
				if(DEBUG) println "Handlers: " + handlers.get(i).name + " " + handlers.get(j).name
				
				//starts by calling the handler crossed with itself
				AnalysisResult ar = analysisHelper(handlers.get(i), handlers.get(j))
				results.add(ar)
				if(!ar.isSafe)
					unsafeResults.add(ar)

			}	
		}
	}
	
	void print() {
		
		if(LONG_OUT) {
		println "\n-----------SAFE--STATE--CASES-----------\n"
		
			results.each { res->
				if(res.isSafe)
					println res
			}
		}
		
		if(unsafeResults.size()>0) {
			println "\n----------UNSAFE--STATE--CASES----------\n"
			unsafeResults.each { res->
				println res
			}
		} else {
			println "\n--------NO--UNSAFE--STATE--CASES----------\n"
		}
	}
	
	//TODO: check the output validity for Hall Light Welcome application
	//returns false positive when modifying different states and says writes to the same field
	//the error should be writing variable information to the state field
	AnalysisResult analysisHelper(Handler h1, Handler h2) {
		
		AnalysisResult ar = new AnalysisResult(h1, h2)
		
		ar = stateAnalysis(ar, h1, h2)
		
		ar = userImpactAnalysis(ar, h1, h2)
		
		return ar
	}
	
	AnalysisResult stateAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
		StateRes s1 = StateRes.NO_STATE
		StateRes s2 = StateRes.NO_STATE
		
		if(h1.writeStates.size() > 0 && h1.readStates.size() > 0) {
			s1 = StateRes.SAFE_RW
		} else if(h1.writeStates.size() > 0) {
			s1 = StateRes.SAFE_WRITE
		} else if(h1.readStates.size() > 0) {
			s1 = StateRes.SAFE_READ
		} 
		
		if(h2.writeStates.size() > 0 && h2.readStates.size() > 0) {
			s2 = StateRes.SAFE_RW
		} else if(h2.writeStates.size() > 0) {
			s2 = StateRes.SAFE_WRITE
		} else if(h2.readStates.size() > 0) {
			s2 = StateRes.SAFE_READ
		} 

		//IF both handler are reading only then they are safe
		
		//if h1 writes state check if the states it writes to is read by the other handler
		//if so set both to unsafe
		// if not leave as is
		if((s1 == StateRes.SAFE_WRITE || s1 == StateRes.SAFE_RW)) {
			h1.writeStates.each { ws->
			//	println ws.path + " " + ws
				ar = stateWriteHelper(ar, h2 ,ws)
				if(!ar.flag) {
								
					//set the state results to unsafe
					if(s1 == StateRes.SAFE_WRITE)
						s1 = StateRes.UNSAFE_W
					else if(s1 == StateRes.SAFE_RW)
						s1 = StateRes.UNSAFE_RW
						
					if(s2 == StateRes.SAFE_READ) 
						s2 = StateRes.UNSAFE_R
					else if(s2 == StateRes.SAFE_RW)
						s2 = StateRes.UNSAFE_RW
				}
				
				if(s2 == StateRes.SAFE_WRITE || s2 == StateRes.SAFE_RW) {
					ar = stateWriteHelper(ar, h2 ,ws)
					if(!ar.flag) {
						
						//println "state write helper passed false\n"
						//set the state results to unsafe
						if(s1 == StateRes.SAFE_WRITE)
							s1 = StateRes.UNSAFE_W
						else if(s1 == StateRes.SAFE_RW)
							s1 = StateRes.UNSAFE_RW

						if(s2 == StateRes.SAFE_WRITE)
							s2 = StateRes.UNSAFE_W
						else if(s2 == StateRes.SAFE_RW)
							s2 = StateRes.UNSAFE_RW
					}
				}
			}
		}
		
		//Repeat of the above check for 2nd handler
		if((s2 == StateRes.SAFE_WRITE || s2 == StateRes.SAFE_RW)) {
			h2.writeStates.each { ws->
				ar = stateReadHelper(ar, h1 ,ws)
				if(!ar.flag) {
							
					//set the state results to unsafe
					if(s2 == StateRes.SAFE_WRITE)
						s2 = StateRes.UNSAFE_W
					else if(s2 == StateRes.SAFE_RW)
						s2 = StateRes.UNSAFE_RW
						
					if(s1 == StateRes.SAFE_READ) 
						s1 = StateRes.UNSAFE_R
					else if(s1 == StateRes.SAFE_RW)
						s1 = StateRes.UNSAFE_RW
				}
				if(s1 == StateRes.SAFE_WRITE || s1 == StateRes.SAFE_RW) {
					ar = stateWriteHelper(ar, h1 ,ws)
					if(!ar.flag) {
					//	println "state write helper passed false\n"
						//set the state results to unsafe
						if(s2 == StateRes.SAFE_WRITE)
							s2 = StateRes.UNSAFE_W
						else if(s2 == StateRes.SAFE_RW)
							s2 = StateRes.UNSAFE_RW

						if(s1 == StateRes.SAFE_WRITE)
							s1 = StateRes.UNSAFE_W
						else if(s1 == StateRes.SAFE_RW)
							s1 = StateRes.UNSAFE_RW
					}
				}
			}
		}
		
		//println "S1: " + s1 + " S2: " + s2
		
		ar.stateRes(s1, s2)
		
		return ar
	}
	
	//checks for a given write state variable and the crossed handler's read states
	//return true if determined safe
	AnalysisResult stateReadHelper(AnalysisResult ar, Handler h, State st) {
		
		//if there are read states in the handler
		//cycle over each of them and check if modify the same state field
		
		ar.flag = true
		
		//no read state is safe for write
		if(h.readStates.size()>0) {
			
			h.readStates.each { rs->
			//	println "" + rs + " " + rs.path
				
				//does it modify the same state field?
				//if different fields are accessed then safe
				//if rs equals st then check
				if(rs.equals(st)) {
					
					//If it is not a safely scheduled write then check
					//if schedule is safe then safe
					if(stateSchSafe(st) == Schedule.NO_SCH) {
						
							ar.readWriteFlag = true
							
							//if it is a constant value that is set to the field
							//return safe
							if(!(st.writeExp instanceof ConstantExpression)) {
								ar.isSafe = false
								ar.flag = false//not safe with variable modifications
								ar.varSetFlag = true //check the variable set to state field flag
							}
							
							if(rs.boolRead) {
								if(!(rs.readExp instanceof ConstantExpression)) {
									if(rs.readExp.getText().contains("now") || rs.readExp.getText().contains("time")) {
										//if inside a time dependent conditional block
										ar.flag = false
										ar.timeCondFlag = true
										ar.isSafe = false
									}
								}
							}
							
					} else if(stateSchSafe(st) == Schedule.OW_FALSE) {
						//if unsafe scheduling
						ar.flag = false
						ar.isSafe = false
						ar.schFlag = true //check the no overwrite scheduling flag
					}
					
					if(timeCheck(st)) {
						//if inside a time dependent conditional block
						ar.flag = false
						ar.timeCondFlag = true
						ar.isSafe = false
					}
				} 
			}
		} 
		
		return ar
	}
	
	AnalysisResult stateWriteHelper(AnalysisResult ar, Handler h, State st) {
		//if there are read states in the handler
		//cycle over each of them and check if modify the same state field
		
		ar.flag = true
			
		h.writeStates.each { ws->
		
			
			//does it modify the same state field?
			//if different fields are accessed then safe
			//if ws equals st then check
			if(ws.equals(st)) {
				
				//If it is not a safely scheduled write then check
				//if schedule is safe then safe
				if(stateSchSafe(st) == Schedule.NO_SCH) {
					
					//does it have more than one unscheduled write to the same field
					if(writeCount(h, st) != StateWFlag.MULTI_W_DIFF_VAL) {
						
						if(DEBUG) {
							println "State: " + st + " " + st.writeVal
							println "State Exp: " + st.writeExp
							println !(st.writeExp instanceof ConstantExpression)
						}
						//if it is a constant value that is set to the field
						//return safe
						if(!(st.writeExp instanceof ConstantExpression)) {
							ar.flag = false//not safe with variable modifications
							ar.varSetFlag = true //check the variable set to state field flag
							ar.isSafe = false
						}
					} 
					else {
						ar.flag = false //not safe with multiple unscheduled modifications
						ar.multModsFlag = true //check the multipl modification flag
						ar.isSafe = false
					}
				} else if(stateSchSafe(st) == Schedule.OW_FALSE) {
					//if unsafe scheduling
					ar.flag = false
					ar.isSafe = false
					ar.schFlag = true //check the no overwrite scheduling flag
				} 
				
				if(timeCheck(st)) {
					//if inside a time dependent conditional block
					ar.flag = false
					ar.isSafe = false
					ar.timeCondFlag = true
				}
				
			} 
		}
		
		return ar
	}
	
	//If there are multiple accesses to the same field but they all set the same value, 
	//then fine as no race condition happens
	//returns a StateWFlag enum
	StateWFlag writeCount(Handler h, State s) {
		
		//number of writes to the state field
		int i = 0
		
		//if all the writes are same value setting if multiple writes happens
		//to the same field
		boolean sameVal = true
		
		h.writeStates.each { ws ->
			if(s.equals(ws)) {
				if(stateSchSafe(ws) == Schedule.NO_SCH)
					i++
				if(s.writeVal.equals(ws.writeVal))
					sameVal = false
			}
		}
//		println "Write Count: " + i + " " + s
		if(i == 1)
			return StateWFlag.SINGLE
		else if(i > 1 && sameVal)
			return StateWFlag.MULTI_W_SAME_VAL
		else
			return StateWFlag.MULTI_W_DIFF_VAL 	
		
	}
		
	//Returns true if the state is inside a time related conditional
	//false if not
	boolean timeCheck(State st) {
		if(st.getPath().contains("t-")) {
			return true
		} else {
			return false
		}
	}
	
	//return 0 if safe scheduler with overwrite
	//return 1 if unsafe no overwrite scheduling
	//return 2 if no scheduling
	Schedule stateSchSafe(State s) {
		
		if(s.path.contains("so:")) {
			return Schedule.OW_TRUE //safe overwrite scheduler
		} else if(s.path.contains("sf:")) {
			return Schedule.OW_FALSE //unsafe no overwrite scheduler
		} else {
			return Schedule.NO_SCH //no scheduler
		}
		
	}
	
	
	enum UImpact{
		SAFE,
		UNS_NOTIF,
		UNS_DEVICE_MOD,
		UNS_DEV_MOD_NOTIF
	}
	
	//TODO: Write the user impact analysis
	//TODO: Add flags to AnalysisResult to store the user impact consistency information
	AnalysisResult userImpactAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
		
		//if any of the two uses notifications
		if(h1.hasMsg) {
			ar = notifAnalysis(ar, h2)
		}
		if(h2.hasMsg) {
			ar = notifAnalysis(ar, h2)
		}
		
		//TODO: Check the path branching of the device modifications
		//TODO: Check if the multiple different values are sent to the same device from the handlers
		
		
		return ar
	}
	
	
	//Safe if the notifications are scheduled execution
	AnalysisResult notifAnalysis(AnalysisResult ar, Handler h) {
		
		//TODO: Implement a check for the scheduling of message sending
		//TODO: set a handler specific flag in AnalysisResult
		//TODO: OR maybe store a flag inside the handler object for the scheduling of notification
		
		return ar
	}

	
	//An object to store the result of the analysis
	//The handlers involved hdl1 and hdl2
	//The issues come across
	class AnalysisResult{
		
		Handler hdl1
		Handler hdl2
		
		//boolean flag to use during checks and keep the modifications
		//to other flag instances consistent within the object
		boolean flag
		
		boolean isSafe
		
		//Output flags
		boolean schFlag //schedule overwrite false flag 
		boolean multModsFlag //multiple modification of the same field flag
		boolean varSetFlag //a variable is set to the state field
		boolean timeCondFlag //a time conditional modification of the state field
		boolean readWriteFlag //a flag for when the methods both rad and write to the same state field
		
		int stateMod
		int usrImp
		int deviceMod
		
		String result
		
		public AnalysisResult(Handler h1, Handler h2) {
			
			hdl1 = h1
			hdl2 = h2
			result = ""
			
			flag = true
			
			schFlag = false
			multModsFlag = false
			varSetFlag = false
			timeCondFlag = false
			readWriteFlag = false
			
			isSafe = true
		}
		
		
		void stateRes(StateRes h1, StateRes h2) {
			
			//println "stateRes: " + h1 + " " + h2
			
			//0: no state usage
			//1: safe read state only
			//2: safe write state only
			//3: safe read and write
			//4: unsafe read state only
			//5: unsafe write state only
			//6: unsafe read and write
			if(h1 == StateRes.NO_STATE && h2 == StateRes.NO_STATE) {
				result += "STATE SAFE!\nNo state usage in the handlers " 
				result += "\n" + hdl1.name + " " + hdl2.name + "\n"
			}
			else if(h1 <= StateRes.SAFE_RW && h2 <= StateRes.SAFE_RW) {
				result += "STATE SAFE!\n"
				switch(h1) {
					case StateRes.NO_STATE:
						result += "Handler 1 does not use state\n"
						break;
					case StateRes.SAFE_READ:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						break;
					case StateRes.SAFE_WRITE:
						result += "Handler 1 " + hdl1.name + " writes state variables "
						result += writeHelper(hdl1)
						break;
					case StateRes.SAFE_RW:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						result += " writes state variables "
						result += writeHelper(hdl1)
						break;
				}
				result += "\n"
				switch(h2) {
					case StateRes.NO_STATE:
						result += "Handler 2 does not use state"
						break;
					case StateRes.SAFE_READ:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						break;
					case StateRes.SAFE_WRITE:
						result += "Handler 2 " + hdl2.name + " writes state variables "
						result += writeHelper(hdl2)
						break;
					case StateRes.SAFE_RW:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						result += " writes state variables "
						result += writeHelper(hdl2)
						break;
				}
				result += "\n"
			} else {
				result += "POSES STATE CONSISTENCY RISKS!\n"
				result += flagChecks()
				switch(h1) {
					case StateRes.UNSAFE_R:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						break;
					case StateRes.UNSAFE_W:
						result += "Handler 1 " + hdl1.name + " writes state variables "
						result += writeHelper(hdl1)
						break;
					case StateRes.UNSAFE_RW:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						result += " writes state variables "
						result += writeHelper(hdl1)
						break;
				}
				result += "\n"
				switch(h2) {
					case StateRes.UNSAFE_R:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						break;
					case StateRes.UNSAFE_W:
						result += "Handler 2 " + hdl2.name + " writes state variables "
						result += writeHelper(hdl2)
						break;
					case StateRes.UNSAFE_RW:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						result += " writes state variables "
						result += writeHelper(hdl2)
						break;
				}
				result += "\n"
			}
		}
		
		String flagChecks() {
			String str = ""
			if(schFlag) {
				str += "Schedule Overwrite is set to false! Queued execution of modifications!\n"
			}
			if(multModsFlag) {
				str += "Multiple unscheduled writes to the same state field!\n"
			}
			if(varSetFlag) {
				str += "A variable value is set to a state field, might pose inconsistency risks!\n"
			}
			if(timeCondFlag) {
				str += "The modification is inside a conditional block that is dependent on time information.\n"
			}
			if(readWriteFlag) {
				str += "One handler writes to a state field the other reads!\n"
			}
			return str
		}
		
		String readHelper(Handler h) {
			String str = ""
			
			h.readStates.each { s-> 
				if(s.path.contains("b:"))
					str += " boolean "
				
				if(s.path.contains("so:") || s.path.contains("sf:"))
					str += " schedule "
										
				str += "" + s + "; "
			}
			
			return str
		}
		
		String writeHelper(Handler h) {
			String str = ""
			
			h.writeStates.each { s->
				if(s.path.contains("b:"))
					str += " boolean "
				
				if(s.path.contains("so:") || s.path.contains("sf:"))
					str += " schedule "
					
				str += "" + s + "; "
			}
			
			return str
			
		}
		
		void usrImpRes(int res) {
			usrImp = res
		}
		
		void devModRes(int res) {
			deviceMod = res
		}
		
		@Override
		String toString() {
			return result
		}
		
	}
	
}
