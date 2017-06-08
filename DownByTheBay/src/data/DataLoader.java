package data;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import linguistic.phonetic.Phoneme;
import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.Pronunciation;
import linguistic.phonetic.syllabic.Syllable;
import linguistic.phonetic.syllabic.WordSyllables;
import markov.BidirectionalVariableOrderPrefixIDMap;
import markov.HierarchicalBidirectionalVariableOrderPrefixIDMap;
import markov.NonHierarchicalBidirectionalVariableOrderPrefixIDMap;

public class DataLoader {

	public static class DataSummary {
		/**
		 * This map serves to store all possible tokens seen in the data set. Representing
		 * each prefix as an integer saves time and space later on. Essentially each new token (after
		 * checking it is not already in the map) is added thus: int tokenID = statesByIndex.addPrefix(prefix), 
		 * where prefix is an object of type LinkedList<SyllableToken>.
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
//	public static DataSummary loadAndAnalyzeData(int markovOrder, String corpusName) {
//		BidirectionalVariableOrderPrefixIDMap<SyllableToken> statesByIndex;
//		Map<Integer, Map<Integer, Double>> transitions = new HashMap<>();
//
//		try {
//			String text = readFileToString(corpusName);
//			statesByIndex = tokenize(markovOrder, text);
//		}
//		catch (IOException e) {
//			e.printStackTrace();
//			return null;
//		}
//
//		// priors, and transitions (these are the data structures required by the Markov model)
//		// See the description of these data structures in the DataSummary class
//
//		// NOTE: For start and stop tokens, use the constants START_TOKEN and END_TOKEN defined in the AbstractMarkovModel class
//
//		// TODO pre-process corpus as sequences of syllables binarize stress to [0,1]
//
//		DataSummary summary = new DataSummary(statesByIndex, transitions);
//		return summary;
//	}

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

//	private static BidirectionalVariableOrderPrefixIDMap<SyllableToken> tokenize(int markovOrder, String text) {
//		String[] words = text.split("\\s+");
//		BidirectionalVariableOrderPrefixIDMap<SyllableToken> result = new NonHierarchicalBidirectionalVariableOrderPrefixIDMap<>(markovOrder);
//		int n = 0;
//
//		for (String s : words) {
//			//get phonemes from CMU pronouncing dictionary
//			WordSyllables syllables = Phoneticizer.getSyllables(s).get(0); //TODO: this only gets the first pronunciation
//			for (int i = 0; i < syllables.size(); i++) {
//				Syllable syl = syllables.get(i);
//				SyllableToken token = new SyllableToken(syl.getPhonemeEnums(), null, syllables.size(), i, syl.getStress());
//				result.put(token);
//				n++;
//			}
//
//		}
//
//		//TODO get parts of speech w/ StanfordCoreNLP
//
//		return result;
//	}

	public static void main(String[] args) {
		// TODO Use the main method to test your class
	}
}
