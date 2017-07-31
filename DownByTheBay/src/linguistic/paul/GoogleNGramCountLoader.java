package linguistic.paul;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import data.CommandlineExecutor;

/**
 * Must have google_ngram_downloader installed for python if not relying solely on local ngram count file
 * @author norkish
 *
 */
public class GoogleNGramCountLoader {

	final private static String localNGramCountFile = "data/ngrams/$N$-grams.txt"; 
	final private static String tmpFile = "data/ngrams/.tmp.txt"; 
	final private static String outTmpFile = "data/ngrams/.outtmp.txt"; 
	final private static Map<Integer, Map<String, Integer>> nGramCounts = new HashMap<Integer, Map<String,Integer>>();
	final private static Integer UNAVAILABLE_NGRAM_COUNT = -1;
	
	public static void main(String[] args) {
		String testString = "xylophone. xenophobia xitixx x-ray xray xerox love";
		int nGramN = 1;
		
		String[] testStringArray = testString.split("[^a-zA-Z'-]+");
		Map<String, Integer> nGramCountsForTestStrings = getNGramCounts(testStringArray, nGramN);
		for (String string : nGramCountsForTestStrings.keySet()) {
			System.out.println(string + "\t" + nGramCountsForTestStrings.get(string));
		}
	}

	private static Map<String, Integer> getNGramCounts(String[] testStringArray, int nGramN) {
		Map<String, Integer> nGramCountsToReturn = new HashMap<String, Integer>();
		Map<String, Integer> nGramCountsNotInLocalFile = new HashMap<String, Integer>();
		
		// load what's on file
		Map<String, Integer> nGramCountsForNGramN = getNGramCountsForNGramN(nGramN);
		
		for (String nGram : testStringArray) {
			// if nGram in question was in file
			if (nGramCountsForNGramN.containsKey(nGram)) {
				// prepare to return it
				nGramCountsToReturn.put(nGram, nGramCountsForNGramN.get(nGram));
			} else {
				// mark it to be looked up
				nGramCountsNotInLocalFile.put(nGram, UNAVAILABLE_NGRAM_COUNT);
			}
		}
		
		// look up any ngrams that are new and not in local file
		if (!nGramCountsNotInLocalFile.isEmpty()) {
			// call external program to query google for missing ngrams
			queryGoogleForMissingNGrams(nGramCountsNotInLocalFile, nGramN);
			for (String nGram : nGramCountsNotInLocalFile.keySet()) {
				// prepare to also return these
				nGramCountsToReturn.put(nGram, nGramCountsNotInLocalFile.get(nGram));

			}
		}
		
		return nGramCountsToReturn;
	}

	private static void queryGoogleForMissingNGrams(Map<String, Integer> nGramCountsNotInLocalFile, int nGramN) {
		// save ngrams to tmp file
		StringBuilder str = new StringBuilder();
		final Path tmpFilePath = Paths.get(tmpFile);
		// prepare the file contents for  input to the external program
		for (String key : nGramCountsNotInLocalFile.keySet()) {
			str.append(key);
			str.append('\n');
		}
		
		try {
			// write the tmp file for the exteernal program
			Files.write(tmpFilePath, str.toString().getBytes(), StandardOpenOption.CREATE_NEW);

			System.out.println("Querying google for " + nGramCountsNotInLocalFile.size() + " ngrams without counts in local file");
			
			// run external python query for missing ngrams, saving output to tmp file
			CommandlineExecutor.execute("python script/query_ngram.py " + tmpFilePath + " " + nGramN, outTmpFile);
			System.out.print("Query complete:");
			
			// process tmp file, loading contents into map.
			Map<String, Integer> nGramCountsForNGramN = nGramCounts.get(nGramN);
			
			// if not already loaded, load from local file
			if (nGramCountsForNGramN == null) {
				nGramCountsForNGramN = new HashMap<String, Integer>();
				nGramCounts.put(nGramN, nGramCountsForNGramN);
			}

			try {
				BufferedReader br = new BufferedReader(new FileReader(outTmpFile));
				String nextLine;
				int nonzero = 0;
				while((nextLine = br.readLine()) != null) {
					String[] tokens = nextLine.split("\\s+");
					final int count = Integer.parseInt(tokens[1]);
					if (count != 0 ) {
						nonzero++;
						nGramCountsNotInLocalFile.put(tokens[0], count);
					} else {
						nGramCountsNotInLocalFile.put(tokens[0], UNAVAILABLE_NGRAM_COUNT);
					}
				}
				br.close();
				System.out.println("" + nonzero + " ngrams found with counts");
			} catch (IOException e) {
				e.printStackTrace();
			}
		
			// remove tmp file
			Files.delete(tmpFilePath);
			Files.delete(Paths.get(outTmpFile));
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// write missing ngrams to local file
		saveNGramCountsForNGramN(nGramCountsNotInLocalFile, nGramN);
		
	}

	private static void saveNGramCountsForNGramN(Map<String, Integer> nGramCountsNotInLocalFile, int nGramN) {
		try {
			StringBuilder str = new StringBuilder();
			for (Entry<String, Integer> entry : nGramCountsNotInLocalFile.entrySet()) {
				str.append(entry.getKey());
				str.append('\t');
				str.append(entry.getValue());
				str.append('\n');
			}
			final Path path = Paths.get(localNGramCountFile.replace("$N$", ""+nGramN));
			if (!Files.exists(path)) {
				Files.write(path, str.toString().getBytes(), StandardOpenOption.CREATE_NEW);
			} else {
				Files.write(path, str.toString().getBytes(), StandardOpenOption.APPEND);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}

	private static Map<String, Integer> getNGramCountsForNGramN(int nGramN) {
		// see if already loaded from local file
		Map<String, Integer> nGramCountsForNGramN = nGramCounts.get(nGramN);
		
		// if not already loaded, load from local file
		if (nGramCountsForNGramN == null) {
			nGramCountsForNGramN = new HashMap<String, Integer>();
			nGramCounts.put(nGramN, nGramCountsForNGramN);
			if (Files.exists(Paths.get(localNGramCountFile.replace("$N$", ""+nGramN)))) {
				try {
					BufferedReader br = new BufferedReader(new FileReader(localNGramCountFile.replace("$N$", ""+nGramN)));
					String nextLine;
					while((nextLine = br.readLine()) != null) {
						String[] tokens = nextLine.split("\t");
						nGramCountsForNGramN.put(tokens[0], Integer.parseInt(tokens[1]));
					}
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return nGramCountsForNGramN;
	}
	
}
