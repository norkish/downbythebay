package linguistic.phonetic.syllabic;

import java.util.List;
import java.util.Map;
import java.util.*;
import linguistic.phonetic.*;
import utils.Utils;

public abstract class Rhymer {

	public static Map<Double, Set<String>> getAllRhymesByThreshold(SyllableList w, double threshold) throws NoRhymeFoundException {
		if (Utils.nullOrEmpty(w)) throw new NoRhymeFoundException();

		if (threshold > 1.0)
			threshold = 1.0;
		else if (threshold < 0.0)
			threshold = 0.0;

		Map<Double,Set<String>> result = new HashMap<>();
		for (Map.Entry<String, List<Pronunciation>> entry : Phoneticizer.cmuDict.entrySet()) {
			if (!Phoneticizer.syllableDict.keySet().contains(entry.getKey().toUpperCase()) || !entry.getKey().matches("\\w+")) continue;
			Word temp = new Word(entry.getKey());
			temp.setPronunciations(Phoneticizer.getSyllablesForWord(entry.getKey()));
			double score = score2Rhymes(w, temp.getRhymeTail());
			if (score >= threshold) {
				if (result.containsKey(score)) {
					result.get(score).add(entry.getKey().toLowerCase());
				}
				else {
					Set<String> temp_set = new TreeSet<>();
					temp_set.add(entry.getKey().toLowerCase());
					result.put(score, temp_set);
				}
			}
		}
		return result;
	}

	public static double score2Rhymes(SyllableList s1, SyllableList s2) {

		if (Utils.nullOrEmpty(s1) || Utils.nullOrEmpty(s2))
			return 0;

		SyllableList shorter = s1;
		SyllableList longer = s2;
		if (s1.size() > s2.size()) {
			shorter = s2;
			longer = s1;
		}

		double d = 0.0;
		double n = shorter.size();
//        double m = .75;
		for (int s = shorter.size() - 1; s >= 0; s--) {
//            if (s == 0)
//                m = 1.25;
			d += (score2Syllables(s1.get(s), s2.get(s)) );
		}

		double average = d / n;

		//penalize words with rhymes of differing syllables
		double difference = Math.abs((double)shorter.size() - (double)longer.size());

		if (difference == 0)
			return average;

		double ratio = (((difference / 2.0) + ((double)shorter.size())) / ((double)longer.size()));
		average *= ratio;

		return average;
	}

	private static double score2Syllables(Syllable s1, Syllable s2) {
		int n = 3;

		double onsetWeight = 1;
		double nucleusWeight = 6;
		double codaWeight = 1;

		ConsonantPronunciation o1 = s1.getOnset();
		ConsonantPronunciation o2 = s2.getOnset();
		double onsetScore;
		if (Utils.nullOrEmpty(o1) && Utils.nullOrEmpty(o2)) {
			n--;
			onsetScore = 0;
			onsetWeight = 0;
		}
		else
			onsetScore = scoreConsonantPronunciations(o1,o2);

		VowelPhoneme n1 = s1.getNucleus();
		VowelPhoneme n2 = s2.getNucleus();
		double nucleusScore;
		if (n1 == null && n2 == null) {
			n--;
			nucleusScore = 0;
			nucleusWeight = 0;
		}
		else
			nucleusScore = score2Vowels(n1,n2);

		ConsonantPronunciation c1 = s1.getCoda();
		ConsonantPronunciation c2 = s2.getCoda();
		double codaScore;
		if (Utils.nullOrEmpty(c1) && Utils.nullOrEmpty(c2)) {
			n--;
			codaScore = 0;
			codaWeight = 0;
		}
		else
			codaScore = scoreConsonantPronunciations(c1,c2);


		double total = (onsetWeight + nucleusWeight + codaWeight) / n;

		double onsetMult = onsetWeight / total;
		double nucleusMult = nucleusWeight / total;
		double codaMult = codaWeight / total;

		double syllableAlignmentScore = ((onsetMult * onsetScore) + (nucleusMult * nucleusScore) + (codaMult * codaScore)) / n;
		return syllableAlignmentScore;
	}

	private static double scoreConsonantPronunciations(ConsonantPronunciation o1, ConsonantPronunciation o2) {
		if (Utils.nullOrEmpty(o1) && Utils.nullOrEmpty(o2))
			return 1;
		if (Utils.nullOrEmpty(o1) || Utils.nullOrEmpty(o2))
			return 0;
		//TODO ACTUALLY ALIGN consonants here
		double alignmentScore = 0;
		ConsonantPronunciation shortest = o1;
		ConsonantPronunciation longest = o2;
		if (o1.size() > o2.size()) {
			shortest = o2;
			longest = o1;
		}
		for (int cp = shortest.size() - 1; cp >= 0; cp--) {
			ConsonantPhoneme cp1 = o1.get(cp);
			ConsonantPhoneme cp2 = o2.get(cp);
			alignmentScore += score2Consonants(cp1, cp2);
		}
		int n = longest.size();
		double average = alignmentScore / n;
		return average;
	}

	private static double score2Vowels(VowelPhoneme n1, VowelPhoneme n2) {
		if (n1 == null || n2 == null)
			return 0;
		double[] coord1 = PhonemeEnum.getCoord(n1.phonemeEnum);
		double[] coord2 = PhonemeEnum.getCoord(n2.phonemeEnum);
		if (coord1 == null || coord2 == null)
			return 0;
		double frontnessDiff = Math.abs(coord1[0] - coord2[0]);
		double hightDiff = Math.abs(coord1[1] - coord2[1]);
		double frontScore = Math.pow(frontnessDiff, 2);
		double heightScore = Math.pow(hightDiff, 2);
		double euclidianDistance = Math.sqrt(frontScore + heightScore);//TODO weight frontness and height
		double normalizedDistance = euclidianDistance / 20;
		double vowelMatchScore = 1 - normalizedDistance;
		return vowelMatchScore;
	}

	private static double getDistance(double[] p1, double[] p2) {
		double d = Math.sqrt(Math.pow(p2[0] - p1[0], 2) + Math.pow(p2[1] - p1[1], 2));
		return d;
	}

	private static double score2Consonants(ConsonantPhoneme ph1, ConsonantPhoneme ph2) {
		if (ph1 == null || ph2 == null)
			return 0;
		MannerOfArticulation m1 = PhonemeEnum.getManner(ph1.phonemeEnum);
		MannerOfArticulation m2 = PhonemeEnum.getManner(ph2.phonemeEnum);
		PlaceOfArticulation p1 = PhonemeEnum.getPlace(ph1.phonemeEnum);
		PlaceOfArticulation p2 = PhonemeEnum.getPlace(ph2.phonemeEnum);
		boolean v1 = ph1.isVoiced();
		boolean v2 = ph2.isVoiced();
		double score = 0;
		if (v1 == v2)
			score += .05;
		if (m1 == m2)
			score += .625;
		if (p1 == p2)
			score += .325;
		return score;
	}

}
