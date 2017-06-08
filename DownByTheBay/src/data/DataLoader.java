package data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import data.DataLoader.DataSummary;
import edu.stanford.nlp.util.CoreMap;
import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.syllabic.WordSyllables;
import linguistic.syntactic.Pos;
import linguistic.syntactic.StanfordNlpInterface;
import markov.BidirectionalVariableOrderPrefixIDMap;
import markov.NonHierarchicalBidirectionalVariableOrderPrefixIDMap;
import markov.Token;
import utils.Pair;
import utils.Utils;

public class DataLoader {

	public static StanfordNlpInterface nlp = new StanfordNlpInterface();

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
	private static final int MAX_TRAINING_SENTENCES = 5000;
	
	public static DataSummary loadData(int order) {
		
//		String[] trainingSentences = new String[]{
////				"Have you ever seen a bear combing his hair?",
////				"Have you ever seen a llama wearing polka dot pajamas?",
////				"Have you ever seen a llama wearing pajamas?",
////				"Have you ever seen a moose with a pair of new shoes?",
////				"Have you ever seen a pirate that just ate a veggie diet?",
//				"I'm a bear combin' his hair?",
//				"Why is it so weird to think about a llama wearing polka dot pajamas?",
//				"I have a llama wearing pajamas.",
//				"Have you seen a moose with a pair of new shoes?",
//				"Have you a pirate that just ate a veggie diet?",
//		};
//		
		StringBuilder str = new StringBuilder();
		try {
			BufferedReader br = new BufferedReader(new FileReader("/Users/norkish/Archive/2017_BYU/ComputationalCreativity/data/COCA Text DB/text_fiction_awq/w_fic_2012.txt"));
			String currLine;
			while ((currLine = br.readLine()) != null) {
				str.append(currLine);
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		String fileContents = str.toString();
		fileContents = fileContents.replaceAll("##\\d+(?= )", "");
		fileContents = fileContents.replaceAll("<p> ", "");
		String[] trainingSentences = fileContents.split(" [[\\.,;:!\\-\")(?@]+ ]+");
		
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMap = new NonHierarchicalBidirectionalVariableOrderPrefixIDMap<SyllableToken>(order);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();

		Integer fromTokenID, toTokenID;
		int sentencesTrainedOn = 0;
		for (String trainingSentence : trainingSentences) {
			if (sentencesTrainedOn == MAX_TRAINING_SENTENCES) {
				break;
			}
			List<SyllableToken> trainingSentenceTokens = convertToSyllableTokens(cleanSentence(trainingSentence));
			if (trainingSentenceTokens == null) continue;
			LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
			fromTokenID = prefixIDMap.addPrefix(prefix);
			for (SyllableToken syllableToken : trainingSentenceTokens) {
				prefix.removeFirst();
				prefix.addLast(syllableToken);
				toTokenID = prefixIDMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID);
				fromTokenID = toTokenID;
			}
			sentencesTrainedOn++;
		}
		
		Utils.normalizeByFirstDimension(transitions);
		
		DataSummary summary = new DataSummary(prefixIDMap, transitions);
		return summary;
	}

	final private static String[] suffixes = new String[]{" n't "," ' "," 's "," 've ", " 'd ", " 'll ", " 're ", " 't "," 'm "};
	private static String cleanSentence(String trainingSentence) {
		trainingSentence = " " + trainingSentence + " ";
		for (String suffix : suffixes) {
			if (trainingSentence.contains(suffix)) {
				return null;
			}
//			trainingSentence = trainingSentence.replaceAll(suffix, suffix.substring(1));
		}
		
		trainingSentence = trainingSentence.trim();
		
		if (trainingSentence.isEmpty()) {
			return null;
		}
		
		return trainingSentence;
	}

	private static List<SyllableToken> convertToSyllableTokens(String trainingSentence) {
		if (trainingSentence == null) return null;
		List<CoreMap> taggedSentences = nlp.parseTextToCoreMaps(trainingSentence);
		List<Pair<String,Pos>> taggedWords = nlp.parseCoreMapsToPairs(taggedSentences.get(0));
		//TODO deal with instances where Stanford tagger splits words, like "don't" -> "do" + "n't"
//		final String[] words = trainingSentence.split("\\s+");

		List<SyllableToken> allTokens = new ArrayList<>();
		for (Pair<String,Pos> taggedWord : taggedWords) {
			if (taggedWord.getSecond() == null) continue;
			List<WordSyllables> pronunciations = Phoneticizer.syllableDict.get(taggedWord.getFirst().toUpperCase());
			if (pronunciations == null) {
				pronunciations = Phoneticizer.useG2P(taggedWord.getFirst().toUpperCase());
			}
			if (pronunciations == null) return null;
			for (WordSyllables pronunciation : pronunciations.subList(0, 1)) {
				for (int i = 0; i < pronunciation.size(); i++) {
					//TODO integrate syllable string representation into Ben's syllable objects
					allTokens.add(new SyllableToken(taggedWord.getFirst(), pronunciation.get(i).getPhonemeEnums(), taggedWord.getSecond(), pronunciation.size(), i, pronunciation.get(i).getStress()));
				}
			}
		}
		return allTokens;
	}

}
