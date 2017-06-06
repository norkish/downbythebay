package data;

import java.awt.image.BufferedImageFilter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import data.DataLoader.DataSummary;
import linguistic.paul.Phonetecizer;
import linguistic.paul.StressedPhone;
import linguistic.paul.Syllabifier;
import linguistic.phonetic.PhonemeEnum;
import linguistic.syntactic.Pos;
import markov.BidirectionalVariableOrderPrefixIDMap;
import markov.NonHierarchicalBidirectionalVariableOrderPrefixIDMap;
import markov.Token;
import utils.Triple;
import utils.Utils;

public class DummyDataLoader {

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
		
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMap = new NonHierarchicalBidirectionalVariableOrderPrefixIDMap<SyllableToken>(order);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();

		Integer fromTokenID, toTokenID;
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = convertToSyllableTokens(trainingSentence);
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
		}
		
		Utils.normalizeByFirstDimension(transitions);
		
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
			
			final List<StressedPhone[]> phones = Phonetecizer.getPhones(word,true);
			if (phones.isEmpty()) return null;
			List<StressedPhone[]> firstPhones = phones.subList(0, 1); // for now just take first way of pronouncing it
			for (StressedPhone[] stressedPhones : firstPhones) { // for each way of pronouncing it, get the syllables
				List<Triple<String, StressedPhone[], StressedPhone>> syllables = Syllabifier.syllabify(word, stressedPhones);
				for (int j = 0; j< syllables.size(); j++ ) {
					Triple<String, StressedPhone[], StressedPhone> syllable = syllables.get(j);
					
					// create a syllable token and add it
					final StressedPhone vowelPhoneme = syllable.getThird();
					if (vowelPhoneme == null) return null;
					allTokens.add(new SyllableToken(syllable.getFirst(),convertToPhonemeEnums(syllable.getSecond()), Pos.NN, syllables.size(), j, vowelPhoneme.stress));
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
