package data;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DataLoader {

	public static class DataSummary {
		/**
		 * This map serves to store all possible tokens seen in the data set. Representing
		 * the tokens as integers saves time and space later on. Essentially each new token (after
		 * checking it is not already in the map) is added thus: statesByIndex.put(newToken,statesByIndex.size).
		 * The integer value in the map is what is used as the key for both the priors and the transitions data
		 * structures.
		 */
		public Map<SyllableToken, Integer> statesByIndex;
		
		/**
		 * The inner key k_1 and outer key k_2 are the token IDs (see description of statesByIndex) for the from- and to-tokens respectively.
		 * The value is the transition probability of going from k_1 to k_2 as learned empirically from the data.
		 * Only insert inner and outer keys for non-zero transition probabilities (i.e., the absence
		 * of a key is used to indicate a probability of 0).
		 */
		public Map<LinkedList<Integer>, Map<Integer, Double>> transitions;

		public DataSummary(Map<SyllableToken, Integer> statesByIndex, Map<LinkedList<Integer>, Map<Integer, Double>> transitions) {
			this.statesByIndex = statesByIndex;
			this.transitions = transitions;
		}
	}

	/**
	 * Load corpus, process corpus into SyllableTokens
	 * @param markovOrder the 
	 * @return
	 */
	public static DataSummary loadAndAnalyzeData(int markovOrder) {
		Map<SyllableToken, Integer> statesByIndex = new HashMap<SyllableToken, Integer>();
		Map<LinkedList<Integer>, Map<Integer, Double>> transitions = new HashMap<LinkedList<Integer>, Map<Integer, Double>>();
		
		// TODO BEN: Load corpus, process corpus into SyllableTokens, populate the data structures called statesByIndex, 
		// priors, and transitions (these are the data structures required by the Markov model)
		// See the description of these data structures in the DataSummary class
		
		// NOTE: For start and stop tokens, use the constants START_TOKEN and END_TOKEN defined in the AbstractMarkovModel class
	
		// TODO pre-process corpus as sequences of syllables binarize stress to [0,1]
		
		DataSummary summary = new DataSummary(statesByIndex, transitions);
		return summary;
	}

	public static void main(String[] args) {
		// TODO Use the main method to test your class
	}
}
