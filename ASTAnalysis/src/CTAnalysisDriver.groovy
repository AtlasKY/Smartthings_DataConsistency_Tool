

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration



class CTAnalysisDriver {
	
	public static void main(String[] args) {
		 
		def proj_root = "C:/Users/AtlasKaan/Desktop/EclipseASTWorkspace/ASTAnalysis"
		
		def code_dir = proj_root + "/groovyApps"
		
		//String fil = args[0]
		
		def demo_file = new File(code_dir + "/ShadesControl.groovy")
		
		//def demo_file = new File("C:/Users/AtlasKaan/Desktop/EclipseASTWorkspace/TrentCode/src/hall-light-welcome-home.groovy")
		
		//SmartSecurityCam
		
		File dir = new File(code_dir).eachFile { file ->			
			//Single file check
			if(file.getName().toLowerCase().contains("elder")) {
				
				CTAnalysisAST ctal = new CTAnalysisAST()
				
				try {
					CompilerConfiguration cc = new CompilerConfiguration(CompilerConfiguration.DEFAULT)
					
					cc.addCompilationCustomizers(ctal)
					
					GroovyShell gshell = new GroovyShell(cc)
					
					println "\n\n********************************************************************"
					println("\nAnalysing " + file.getName() + ":")
					
					gshell.evaluate(file)
					
					println("Analysis Done!")
					
				}catch(MissingMethodException mme)
				{	
					def missingMethod = mme.toString()
					
					println(mme.getArguments())
					
					if(!missingMethod.contains("definition()"))
						println("missing method: " + missingMethod)
				}
				ConsistencyAnalysis conAn = new ConsistencyAnalysis(ctal.getHandlers())
				
				conAn.analyse()
				
				conAn.print()
			}
		}
		
		
	}
}
