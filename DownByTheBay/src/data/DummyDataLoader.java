package data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import data.DataLoader.DataSummary;
import edu.stanford.nlp.util.CoreMap;
import linguistic.paul.Phonetecizer;
import linguistic.phonetic.Phoneme;
import linguistic.phonetic.PhonemeEnum;
import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.Pronunciation;
import linguistic.phonetic.syllabic.Syllabifier;
import linguistic.phonetic.syllabic.Syllable;
import linguistic.phonetic.syllabic.WordSyllables;
import linguistic.syntactic.Pos;
import linguistic.syntactic.StanfordNlpInterface;
import markov.BidirectionalVariableOrderPrefixIDMap;
import markov.NonHierarchicalBidirectionalVariableOrderPrefixIDMap;
import markov.Token;
import utils.Pair;
import utils.Triple;
import utils.Utils;

public class DummyDataLoader {

	public static StanfordNlpInterface nlp = new StanfordNlpInterface();
	public static DataSummary loadData(int order) {

		String[] trainingSentences = new String[]{
//				"Have you ever seen a bear combing his hair?",
//				"Have you ever seen a llama wearing polka dot pajamas?",
//				"Have you ever seen a llama wearing pajamas?",
//				"Have you ever seen a moose with a pair of new shoes?",
//				"Have you ever seen a pirate that just ate a veggie diet?",
				"Once I saw a bear combing his hair?",
				"Why is it so weird to think about a llama wearing polka dot pajamas?",
				"I have a llama wearing pajamas.",
				"Have you seen a moose with a pair of new shoes?",
				"Have you a pirate that just ate a veggie diet?",
		};

//		StringBuilder str = new StringBuilder();
//		try {
//			BufferedReader br = new BufferedReader(new FileReader("/Users/norkish/Archive/2017_BYU/ComputationalCreativity/data/COCA Text DB/text_fiction_awq/w_fic_2012.txt"));
//			String currLine;
//			while ((currLine = br.readLine()) != null) {
//				str.append(currLine);
//			}
//			br.close();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		
//		String[] trainingSentences = str.toString().split(" . ");
		
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMap = new NonHierarchicalBidirectionalVariableOrderPrefixIDMap<>(order);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<>();

		Integer fromTokenID, toTokenID;
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = convertToSyllableTokens(trainingSentence);
			if (trainingSentenceTokens == null) continue;
			LinkedList<Token> prefix = new LinkedList<>(Collections.nCopies(order, Token.getStartToken()));
			fromTokenID = prefixIDMap.addPrefix(prefix);
			for (SyllableToken syllableToken : trainingSentenceTokens) {
				prefix.removeFirst();
				prefix.addLast(syllableToken);
				toTokenID = prefixIDMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID);
				fromTokenID = toTokenID;
			}
		}

		Utils.normalizeByFirstDimension(transitions);
		
		DataSummary summary = new DataSummary(prefixIDMap, transitions);
		return summary;
	}

	private static PhonemeEnum[] phonemeEnums = PhonemeEnum.values();

	private static List<SyllableToken> convertToSyllableTokens(String trainingSentence) {
		List<CoreMap> taggedSentences = nlp.parseTextToCoreMaps(trainingSentence);
		List<Pair<String,Pos>> taggedWords = nlp.parseCoreMapsToPairs(taggedSentences.get(0));
		//TODO deal with instances where Stanford tagger splits words, like "don't" -> "do" + "n't"
//		final String[] words = trainingSentence.split("\\s+");

		List<SyllableToken> allTokens = new ArrayList<>();
		for (Pair<String,Pos> taggedWord : taggedWords) {
			if (taggedWord.getSecond() == null) continue;
			List<WordSyllables> pronunciations = Phoneticizer.syllableDict.get(taggedWord.getFirst().toUpperCase());
			for (WordSyllables pronunciation : pronunciations) {
				for (int i = 0; i < pronunciation.size(); i++) {
					//TODO integrate syllable string representation into Ben's syllable objects
					allTokens.add(new SyllableToken(pronunciation.get(i).getPhonemeEnums().toString(), pronunciation.get(i).getPhonemeEnums(), taggedWord.getSecond(), pronunciation.size(), i, pronunciation.get(i).getStress()));
				}
			}
		}
//			if (phones.isEmpty()) return null;
//			List<Phoneme[]> firstPhones = phones.subList(0, 1); // for now just take first way of pronouncing it
//			for (Phoneme[] Phonemes : firstPhones) { // for each way of pronouncing it, get the syllables
//				List<Triple<String, Phoneme[], Phoneme>> syllables = Syllabifier.syllabify(word, Phonemes);
//				for (int j = 0; j< syllables.size(); j++ ) {
//					Triple<String, Phoneme[], Phoneme> syllable = syllables.get(j);
//
//					// create a syllable token and add it
//					final Phoneme vowelPhoneme = syllable.getThird();
//					if (vowelPhoneme == null) return null;
//					allTokens.add(new SyllableToken(syllable.getFirst(),convertToPhonemeEnums(syllable.getSecond()), Pos.NN, syllables.size(), j, vowelPhoneme.stress));
//				}
//			}
		return allTokens;
	}

//	private static List<PhonemeEnum> convertToPhonemeEnums(Phoneme[] phones) {
//		List<PhonemeEnum> enums = new ArrayList<>();
//		for (Phoneme Phoneme : phones) {
//			enums.add(phonemeEnums[Phoneme.getPhonemeEnum()]);
//		}
//
//		return enums;
//	}

}
