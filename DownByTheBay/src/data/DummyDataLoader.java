package data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import data.DataLoader.DataSummary;
import linguistic.Phonetecizer;
import linguistic.StressedPhone;
import linguistic.Syllabifier;
import linguistic.phonetic.PhonemeEnum;
import linguistic.syntactic.Pos;
import markov.BidirectionalVariableOrderPrefixIDMap;
import markov.NonHierarchicalBidirectionalVariableOrderPrefixIDMap;
import markov.Token;
import utils.Triple;
import utils.Utils;

public class DummyDataLoader {

	public static DataSummary loadData() {
		int order = 8;
		
		String[] trainingSentences = new String[]{
				"Have you ever seen a llama wearing polka dot pajamas?",
				"Have you ever seen a table made with laffy taffy cable?"
		};
		
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMap = new NonHierarchicalBidirectionalVariableOrderPrefixIDMap<SyllableToken>(8);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();

		Integer fromTokenID, toTokenID;
		for (String trainingSentence : trainingSentences) {
			LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
			List<SyllableToken> trainingSentenceTokens = convertToSyllableTokens(trainingSentence);
			fromTokenID = prefixIDMap.addPrefix(prefix);
			for (SyllableToken syllableToken : trainingSentenceTokens) {
				prefix.removeFirst();
				prefix.addLast(syllableToken);
				toTokenID = prefixIDMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID);
				fromTokenID = toTokenID;
			}
		}
		
		
		DataSummary summary = new DataSummary(prefixIDMap, transitions);
		return summary;
	}

	private static PhonemeEnum[] phonemeEnums = PhonemeEnum.values();
	private static List<SyllableToken> convertToSyllableTokens(String trainingSentence) {
		List<SyllableToken> allTokens = new ArrayList<SyllableToken>();
		
		List<Pos> Poses = null;// TODO: parse for Pos
		final String[] words = trainingSentence.split("\\s+");
		assert(Poses.size() == words.length);
		for (int i = 0; i < words.length; i++ ) {
			String word = words[i];
//			Pos pos = Poses.get(i);
			
			List<StressedPhone[]> phones = Phonetecizer.getPhones(word,true);
			for (StressedPhone[] stressedPhones : phones) { // for each way of pronouncing it, get the syllables
				List<Triple<String, StressedPhone[], StressedPhone>> syllables = Syllabifier.syllabify(word, stressedPhones);
				for (int j = 0; j< syllables.size(); j++ ) {
					Triple<String, StressedPhone[], StressedPhone> syllable = syllables.get(j);
					
					// create a syllable token and add it
					allTokens.add(new SyllableToken(convertToPhonemeEnums(syllable.getSecond()), Pos.NN, syllables.size(), j, syllable.getThird().stress));
				}
			}
		}
		
		return allTokens;
	}
	private static List<PhonemeEnum> convertToPhonemeEnums(StressedPhone[] phones) {
		List<PhonemeEnum> enums = new ArrayList<PhonemeEnum>();
		for (StressedPhone stressedPhone : phones) {
			enums.add(phonemeEnums[stressedPhone.phone]);
		}
		
		return enums;
	}

}
