
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration

class ConsistencyAnalysis {
	
	List handlers
	List devices
	
	List results
	
	enum StateRes{
		NO_STATE,
		SAFE_READ,
		SAFE_WRITE,
		SAFE_RW,
		UNSAFE_R,
		UNSAFE_W,
		UNSAFE_RW
	}
	
	public ConsistencyAnalysis(List hdls, List devs) {
		
		handlers = new ArrayList()
		handlers = hdls
		
		devices = new ArrayList()
		devices = devs
		
		results = new ArrayList<AnalysisResult>()
		
	}
	
	void analyse() {
		
		for(int i = 0; i < handlers.size(); i++) {
			
			for(int j = i; j < handlers.size(); j++) {
				
				results.add(analysisHelper(handlers.get(i), handlers.get(j)))
				
			}	
		}
	}
	
	AnalysisResult analysisHelper(Handler h1, Handler h2) {
		
		AnalysisResult ar = new AnalysisResult(h1, h2)
		
		stateAnalysis(ar, h1, h2)
		
		userImpactAnalysis(ar, h1, h2)
		
		devModAnalysis(ar, h1, h2)
	}
	
	void stateAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
		
		
		ar.stateRes(h1, h2)
	}
	
	void userImpactAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
	}
	
	void devModAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
	}
	
	//An object to store the result of the analysis
	//The handlers involved hdl1 and hdl2
	//The issues come across
	class AnalysisResult{
		
		Handler hdl1
		Handler hdl2
		
		int stateMod
		int usrImp
		int deviceMod
		
		String result
		
		public AnalysisResult(Handler h1, Handler h2) {
			
			hdl1 = h1
			hdl2 = h2
			result = ""
			
		}
		
		void stateRes(StateRes h1, StateRes h2) {
			
			//0: no state usage
			//1: safe read state only
			//2: safe write state only
			//3: safe read and write
			//4: unsafe read state only
			//5: unsafe write state only
			//6: unsafe read and write
			if(h1 == StateRes.NO_STATE && h2 == StateRes.NO_STATE) {
				result += "No state usage in the handlers\n"
			}
			else if(h1 < StateRes.SAFE_RW && h2 < StateRes.SAFE_RW) {
				result += "State Safe!\n"
				switch(h1) {
					case StateRes.NO_STATE:
						result += "Handler 1 does not use state\n"
						break;
					case StateRes.SAFE_READ:
						result += "Handler 1 " + hdl1.name + "only reads state variables "
						hdl1.readStates.each { s-> result += s + "; "}
						break;
					case StateRes.SAFE_WRITE:
						result += "Handler 1 " + hdl1.name + "only writes state variables "
						result += "writes state variables "
						hdl1.writeStates.each { s-> result += s + "; "}
						break;
					case StateRes.SAFE_RW:
						result += "Handler 1 " + hdl1.name + "reads state variables "
						hdl1.readStates.each { s-> result += s + "; "}
						result += "writes state variables "
						hdl1.writeStates.each { s->	result += rs + "; "}
						break;
				}
				switch(h2) {
					case StateRes.NO_STATE:
						result += "Handler 2 does not use state\n"
						break;
					case StateRes.SAFE_READ:
						result += "Handler 2 " + hdl2.name + "only reads state variables "
						hdl2.readStates.each { s-> result += s + "; "}
						break;
					case StateRes.SAFE_WRITE:
						result += "Handler 2 " + hdl2.name + "only writes state variables "
						result += "writes state variables "
						hdl2.writeStates.each { s-> result += s + "; "}
						break;
					case StateRes.SAFE_RW:
						result += "Handler 2 " + hdl2.name + "reads state variables "
						hdl2.readStates.each { s-> result += s + "; "}
						result += "writes state variables "
						hdl2.writeStates.each { s-> result += s + "; "}
						break;
				}
			} else {
				result += "Poses State consistency risks!\n"
				switch(h1) {
					case 4:
						result += "Handler 1 " + hdl1.name + "reads state variables "
						hdl1.readStates.each { s-> result += s + "; "}
						break;
					case 5:
						result += "Handler 1 " + hdl1.name + "writes state variables "
						result += "writes state variables "
						hdl1.writeStates.each { s-> result += s + "; "}
						break;
					case 6:
						result += "Handler 1 " + hdl1.name + "reads state variables "
						hdl1.readStates.each { s-> result += s + "; "}
						result += "writes state variables "
						hdl1.writeStates.each { s->	result += s + "; "}
						break;
				}
				switch(h2) {
					case 4:
						result += "Handler 2 " + hdl2.name + "only reads state variables "
						hdl2.readStates.each { s-> result += s + "; "}
						break;
					case 5:
						result += "Handler 2 " + hdl2.name + "only writes state variables "
						result += "writes state variables "
						hdl2.writeStates.each { s-> result += s + "; "}
						break;
					case 6:
						result += "Handler 2 " + hdl2.name + "reads state variables "
						hdl2.readStates.each { s-> result += s + "; "}
						result += "writes state variables "
						hdl2.writeStates.each { s-> result += s + "; "}
						break;
				}
			}
			
			
		}
		
		void usrImpRes(int res) {
			usrImp = res
		}
		
		void devModRes(int res) {
			deviceMod = res
		}
		
	}
	
}
