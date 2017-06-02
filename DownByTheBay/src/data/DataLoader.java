package data;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import markov.BidirectionalVariableOrderPrefixIDMap;
import markov.HierarchicalBidirectionalVariableOrderPrefixIDMap;
import markov.Token;

public class DataLoader {

	public static class DataSummary {
		/**
		 * This map serves to store all possible tokens seen in the data set. Representing
		 * each prefix as an integer saves time and space later on. Essentially each new token (after
		 * checking it is not already in the map) is added thus: int tokenID = statesByIndex.addPrefix(prefix), 
		 * where prefix is and object of type LinkedList<SyllableToken>.
		 * The integer value in the map is what is used as the key for both the priors and the transitions data
		 * structures.
		 */
		public BidirectionalVariableOrderPrefixIDMap<SyllableToken> statesByIndex;
		
		/**
		 * The inner key k_1 and outer key k_2 are the prefix IDs (see description of statesByIndex) for the from- and to-tokens respectively.
		 * The value is the transition probability of going from k_1 to k_2 as learned empirically from the data.
		 * Only insert inner and outer keys for non-zero transition probabilities (i.e., the absence
		 * of a key is used to indicate a probability of 0). k_1 and k_2 are both sequences of tokens of length markovOrder.
		 */
		public Map<Integer, Map<Integer, Double>> transitions;

		public DataSummary(BidirectionalVariableOrderPrefixIDMap<SyllableToken> statesByIndex, Map<Integer, Map<Integer, Double>> transitions) {
			this.statesByIndex = statesByIndex;
			this.transitions = transitions;
		}
	}

	/**
	 * Load corpus, process corpus into SyllableTokens
	 * @param markovOrder the 
	 * @return
	 */
	public static DataSummary loadAndAnalyzeData(int markovOrder, String corpusName) {
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> statesByIndex = new HierarchicalBidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		
		// TODO BEN: Load corpus, process corpus into SyllableTokens, populate the data structures called statesByIndex,
		try {
			String text = readFileToString(corpusName);
//			statesByIndex = tokenize(text);
		}
		catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		// priors, and transitions (these are the data structures required by the Markov model)
		// See the description of these data structures in the DataSummary class
		
		// NOTE: For start and stop tokens, use the constants START_TOKEN and END_TOKEN defined in the AbstractMarkovModel class
	
		// TODO pre-process corpus as sequences of syllables binarize stress to [0,1]
		
		DataSummary summary = new DataSummary(statesByIndex, transitions);
		return summary;
	}

	private static String readFileToString(String file) throws IOException {
		InputStream is = new FileInputStream(file);
		BufferedReader buf = new BufferedReader(new InputStreamReader(is));
		String line = buf.readLine(); StringBuilder sb = new StringBuilder();
		while(line != null){
			sb.append(line).append("\n");
			line = buf.readLine();
		}
		String fileAsString = sb.toString();
		return fileAsString;
	}

	private static Map<SyllableToken, Integer> tokenize(String text) {
		//get phonemes from CMU pronouncing dictionary
		//get parts of speech w/ StanfordCoreNLP
		//syllabify
		return null;
	}

	public static void main(String[] args) {
		// TODO Use the main method to test your class
	}
}
