package data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.CoreMap;
import linguistic.phonetic.Phoneme;
import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.VowelPhoneme;
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
	private static final long MAX_TRAINING_SENTENCES = 5000; // -1 for no limit
	private static final int DEBUG = 1; 
	private static final boolean USE_DUMMY_DATA = true; 
	
	public static DataSummary loadData(int order) {
		
		String[] trainingSentences;
		if (USE_DUMMY_DATA) {
			trainingSentences = new String[]{
					"Have you seen a moose with a pair of new shoes?",
					"Have you ever seen a bear combing his hair?",
	//				"Have you ever seen a llama wearing polka dot pajamas?",
	//				"Have you ever seen a llama wearing pajamas?",
	//				"Have you ever seen a moose with a pair of new shoes?",
	//				"Have you ever seen a pirate that just ate a veggie diet?",
					"I'm a bear combin' his hair?",
					"Why is it so weird to think about a llama wearing polka dot pajamas?",
					"I have a llama wearing pajamas.",
					"Have you a pirate that just ate a veggie diet?",
			};
		}

		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMap = new NonHierarchicalBidirectionalVariableOrderPrefixIDMap<SyllableToken>(order);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		
		Integer fromTokenID, toTokenID;
		long sentencesTrainedOn = 0;
		long sentencePronunciationsTrainedOn = 0;

		for (int i = 1990; i <= 2012; i++) {
			if (!USE_DUMMY_DATA) {
				StringBuilder str = new StringBuilder();
				try {
					final String fileName = "data/text_fiction_awq/w_fic_" + i + ".txt";
					if (DEBUG > 0) System.out.println("Now training on " + fileName);
					BufferedReader br = new BufferedReader(new FileReader(fileName));
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
				trainingSentences = fileContents.split(" [[^a-zA-Z']+ ]+");
			}
			
			for (String trainingSentence : trainingSentences) {
				if (sentencesTrainedOn == MAX_TRAINING_SENTENCES) {
					break;
				}
				// get syllable tokens for all unique pronunciations of the sentence
				List<List<SyllableToken>> trainingTokensSentences = convertToSyllableTokens(cleanSentence(trainingSentence));
				// if there was no valid pronunciation, skip it
				if (trainingTokensSentences == null) continue;
				// for each pronunciation
				boolean foundValidPronunciation = false;
				if (DEBUG > 1) System.out.println("PRON COUNT:" + trainingTokensSentences.size());
				for (List<SyllableToken> trainingSentenceTokens : trainingTokensSentences) {
					if (trainingSentenceTokens.isEmpty()) continue;
					foundValidPronunciation = true;
					LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
					fromTokenID = prefixIDMap.addPrefix(prefix);
					for (SyllableToken syllableToken : trainingSentenceTokens) {
						prefix.removeFirst();
						prefix.addLast(syllableToken);
						toTokenID = prefixIDMap.addPrefix(prefix);
						Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID);
						fromTokenID = toTokenID;
					}
					sentencePronunciationsTrainedOn++;
				}
				if (DEBUG > 1) System.out.println("sentencesTrainedOn:" + sentencesTrainedOn + ", sentencePronunciationsTrainedOn:" + sentencePronunciationsTrainedOn + " transitions.size()=" + transitions.size() + " prefixIDMap.getPrefixCount()=" + prefixIDMap.getPrefixCount());
				if (foundValidPronunciation)
					sentencesTrainedOn++;
			}
			if (sentencesTrainedOn == MAX_TRAINING_SENTENCES || USE_DUMMY_DATA) {
				break;
			}
		}
		System.err.println("Trained on " + sentencesTrainedOn + " sentences, " + sentencePronunciationsTrainedOn + " sentence pronunciations");
		Utils.normalizeByFirstDimension(transitions);
		
		DataSummary summary = new DataSummary(prefixIDMap, transitions);
		return summary;
	}

	final private static String[] suffixes = new String[]{" n't "," ' "," 's "," 've ", " 'd ", " 'll ", " 're ", " 't "," 'm "};
	private static String cleanSentence(String trainingSentence) {
		if (DEBUG > 1) System.out.println("CLEAN:" + trainingSentence);
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

	private static List<List<SyllableToken>> convertToSyllableTokens(String trainingSentence) {
		if (DEBUG > 1) System.out.println("CONVERT:" + trainingSentence);
		if (trainingSentence == null || trainingSentence.trim().isEmpty()) return null;
		List<CoreMap> taggedSentences = nlp.parseTextToCoreMaps(trainingSentence);
		List<Pair<String,Pos>> taggedWords = nlp.parseCoreMapsToPairs(taggedSentences.get(0));
		//TODO deal with instances where Stanford tagger splits words, like "don't" -> "do" + "n't"
//		final String[] words = trainingSentence.split("\\s+");

		List<List<SyllableToken>> allTokensSentences = new ArrayList<>();
		allTokensSentences.add(new ArrayList<>());
		// for every word
		for (Pair<String,Pos> taggedWord : taggedWords) {
			if (taggedWord.getSecond() == null) continue;
			List<WordSyllables> pronunciations = Phoneticizer.syllableDict.get(taggedWord.getFirst().toUpperCase());
			if (pronunciations == null) {
				pronunciations = Phoneticizer.useG2P(taggedWord.getFirst().toUpperCase());
			}
			if (pronunciations == null || pronunciations.isEmpty()) return null;
			reduceUnnecessaryPronunciations(pronunciations);
			// replicate all of the token sentences so far
			int pronunciationCount = pronunciations.size();
			replicateTokenSentences(allTokensSentences, pronunciationCount);
			
			// for every pronunciation of the word
			for (int i = 0; i < pronunciationCount; i++ ) {
				WordSyllables pronunciation = pronunciations.get(i); 
				// for each syllable in that pronunciation
				for (int j = 0; j < pronunciation.size(); j++) {
					// create a new syllable token
					final SyllableToken newSyllableToken = new SyllableToken(taggedWord.getFirst(), pronunciation.get(j).getPhonemeEnums(), taggedWord.getSecond(), pronunciation.size(), j, pronunciation.get(j).getStress());
					for (int k = i; k < allTokensSentences.size(); k+=pronunciationCount) {
						// and add it to each original sentence
						List<SyllableToken> sentenceTokens = allTokensSentences.get(k);
						sentenceTokens.add(newSyllableToken);
					}
				}
			}
		}
		// if there were no words
		return allTokensSentences;
	}

	/**
	 * Looks for instances where pronunciations are redundant. For example "with" can be stressed and unstressed.
	 * We want to keep the stressed version since in our stress constraint we care only that viable pronunciations
	 * have at least as great a stress as the constraint mandates
	 * @param pronunciations
	 */
	private static void reduceUnnecessaryPronunciations(List<WordSyllables> pronunciations) {
		Set<Integer> idxsToRemove = new HashSet<Integer>();
		List<Phoneme> wordSyllables1, wordSyllables2;
		for (int i = 0; i < pronunciations.size()-1; i++) {
			if (idxsToRemove.contains(i)) continue;
			wordSyllables1 = pronunciations.get(i).getPronunciation();
			for (int j = i+1; j < pronunciations.size(); j++) {
				if (idxsToRemove.contains(j)) continue;
				boolean allSamePhonemeEnums = true;
				boolean wordSyllable1hasLessStressedSyllables = false;
				boolean wordSyllable2hasLessStressedSyllables = false;
				wordSyllables2 = pronunciations.get(j).getPronunciation();
				if (wordSyllables1.size() == wordSyllables2.size()) {
					for (int k = 0; k < wordSyllables1.size(); k++) {
						Phoneme phoneme1 = wordSyllables1.get(k);
						Phoneme phoneme2 = wordSyllables2.get(k);
						if (phoneme1.getPhonemeEnum() == phoneme2.getPhonemeEnum()) {
							if (phoneme1.isVowel()) {
								if (phoneme2.isVowel()) {
									int phoneme1Stress = ((VowelPhoneme) phoneme1).stress;
									int phoneme2Stress = ((VowelPhoneme) phoneme2).stress;
									if (phoneme1Stress > phoneme2Stress) {
										wordSyllable2hasLessStressedSyllables = true;
									} else if (phoneme2Stress > phoneme1Stress) {
										wordSyllable1hasLessStressedSyllables = true;
									}
								}
							}
						} else {
							allSamePhonemeEnums = false;
							break;
						}
					}
				} else {
					allSamePhonemeEnums = false;
				}
				
				if (allSamePhonemeEnums) {
					if (wordSyllable1hasLessStressedSyllables) {
						if (!wordSyllable2hasLessStressedSyllables) {
							// they're all the same and wordSyllable2 is strictly more stressed, so we can get away with using just the first
							idxsToRemove.add(i);
						}
					} else {
						if (wordSyllable2hasLessStressedSyllables) {
							// they're all the same and wordSyllable1 is strictly more stressed, so we can get away with using just the second
							idxsToRemove.add(j);
						}
					}
				}
			}
		}
		
		final ArrayList<Integer> listOfIdxsToRemove = new ArrayList<Integer>(idxsToRemove);
		Collections.sort(listOfIdxsToRemove, Collections.reverseOrder());
		for (Integer idxToRemove : listOfIdxsToRemove) {
			pronunciations.remove((int)idxToRemove);
		}
	}

	/**
	 * Replicates each entry in allTokensSentences so that there are copies times more instances of the entry
	 * with copies of a given entry being placed together, preserving original ordering or unique entries 
	 * @param allTokensSentences
	 * @param copies
	 */
	private static void replicateTokenSentences(List<List<SyllableToken>> allTokensSentences, int copies) {
		if (copies == 1) return;
		else {
			int allTokensSize = allTokensSentences.size();
			for (int i = allTokensSize-1; i >=0; i--) {
				List<SyllableToken> sentenceToReplicate = allTokensSentences.get(i);
				allTokensSentences.add(i,new ArrayList<SyllableToken>(sentenceToReplicate));
			}
		}
	}

}
