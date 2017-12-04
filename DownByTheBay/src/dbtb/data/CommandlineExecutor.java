/*
 * Found at http://vyvaks.wordpress.com/2006/05/27/does-runtimeexec-hangs-in-java/
 * with pieces from http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html?page=4
 */
package dbtb.data;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class CommandlineExecutor{

	public static void execute(String cmd, String stdOutfile){
		List<String> parameters = new ArrayList<String>();
		boolean inParen = false;
		StringBuilder str = null;
		String[] ungroupedParams = cmd.split("\\s+");

		for(String token: ungroupedParams){
			if(!inParen && token.startsWith("\"")){
				str = new StringBuilder(token);
				inParen = true;
			}
			else if(inParen){
				str.append(" " + token);
				if(token.endsWith("\"")){
					parameters.add(str.toString().substring(1,str.length()-1));
					inParen = false;
				}
			}
			else{
				parameters.add(token);
			}
		}
		execute(parameters.toArray(new String[parameters.size()]),stdOutfile);
	}
	/*
	 * Executes a commandline command and allows for catching of output to a file
	 *
	 * @param String cmd - the full command-line command; must not be null
	 * @param String stdOutfile - name of file to which to catch stout output; if null, output is not caught.
	 */
	public static void execute(String[] cmd, String stdOutfile){
		try
		{            
			FileOutputStream fos = (stdOutfile != null ? new FileOutputStream(stdOutfile) : null);
			Runtime rt = Runtime.getRuntime();
			Process proc = rt.exec(cmd);
			// any error message?
			ProcessHandler errorGobbler = new ProcessHandler(proc.getErrorStream(), "ERROR");            

			// any output?
			ProcessHandler outputGobbler = new ProcessHandler(proc.getInputStream(), "OUTPUT", fos);

			// kick them off
			errorGobbler.start();
			outputGobbler.start();

			// any error???
			int exitVal = proc.waitFor();
			if(fos != null){
				fos.flush();
			}    
		} 
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}
}
