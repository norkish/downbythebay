package linguistic.phonetic.syllabic;

import java.util.List;
import java.util.Map;
import java.io.*;
import java.util.*;

import data.SyllableToken;
import genetic.Individual;
import linguistic.phonetic.*;
import utils.Utils;

public class Rhymer {

	private final double frontnessWeight;
	private final double heightWeight;
	private final double placeOfArticulationWeight;
	private final double mannerOfArticulationWeight;
	private final double voicingWeight;
	private final double onsetWeight;
	private final double nucleusWeight;
	private final double codaWeight;
	private final double stressWeight;

	public Rhymer(double frontnessWeight, double heightWeight, double placeOfArticulationWeight, double mannerOfArticulationWeight, double voicingWeight, double onsetWeight, double nucleusWeight, double codaWeight, double stressWeight) {
		this.frontnessWeight = frontnessWeight;
		this.heightWeight = heightWeight;
		this.placeOfArticulationWeight = placeOfArticulationWeight;
		this.mannerOfArticulationWeight = mannerOfArticulationWeight;
		this.voicingWeight = voicingWeight;
		this.onsetWeight = onsetWeight;
		this.nucleusWeight = nucleusWeight;
		this.codaWeight = codaWeight;
		this.stressWeight = stressWeight;
	}

	public Rhymer(Individual weights) {
		Map<String,Double> map = weights.getValues();
		this.frontnessWeight = map.get("frontness");
		this.heightWeight = map.get("height");
		this.placeOfArticulationWeight = map.get("place_of_articulation");
		this.mannerOfArticulationWeight = map.get("manner_of_articulation");
		this.voicingWeight = map.get("voicing");
		this.onsetWeight = map.get("onset");
		this.nucleusWeight = map.get("nucleus");
		this.codaWeight = map.get("coda");
		this.stressWeight = map.get("stress_diff");
	}

	public static double score2SyllablesByClassicWeights(Syllable s1, Syllable s2) {
		Rhymer temp = new Rhymer(1,1,.55,.35,.1,.25,6,1,1);
		return temp.score2Syllables(s1,s2);
	}

	public double score2Syllables(Syllable s1, Syllable s2) {
		int n = 3;

		List<ConsonantPhoneme> o1 = s1.getOnset();
		List<ConsonantPhoneme> o2 = s2.getOnset();
		double onsetScore;
		if (Utils.isNullorEmpty(o1) && Utils.isNullorEmpty(o2)) {
			n--;
			onsetScore = 0;
//			onsetWeight = 0;
		}
		else
			onsetScore = scoreConsonantPronunciations(o1,o2);

		VowelPhoneme n1 = s1.getNucleus();
		VowelPhoneme n2 = s2.getNucleus();
		double nucleusScore;
		if (n1 == null && n2 == null) {
			n--;
			nucleusScore = 0;
//			nucleusWeight = 0;
		}
		else
			nucleusScore = score2Vowels(n1,n2);

		List<ConsonantPhoneme> c1 = s1.getCoda();
		List<ConsonantPhoneme> c2 = s2.getCoda();
		double codaScore;
		if (Utils.isNullorEmpty(c1) && Utils.isNullorEmpty(c2)) {
			n--;
			codaScore = 0;
//			codaWeight = 0;
		}
		else
			codaScore = scoreConsonantPronunciations(c1,c2);


		double total = (onsetWeight + nucleusWeight + codaWeight) / n;

		double onsetMult = onsetWeight / total;
		double nucleusMult = nucleusWeight / total;
		double codaMult = codaWeight / total;

		double syllableAlignmentScore = ((onsetMult * onsetScore) + (nucleusMult * nucleusScore) + (codaMult * codaScore)) / n;

		int stressDiff = Math.abs(s1.getStress() - s2.getStress());
		syllableAlignmentScore -= (stressDiff * stressWeight);

		return syllableAlignmentScore;
	}

	private double scoreConsonantPronunciations(List<ConsonantPhoneme> o1, List<ConsonantPhoneme> o2) {
		if (Utils.isNullorEmpty(o1) && Utils.isNullorEmpty(o2))
			return 1;
		if (Utils.isNullorEmpty(o1) || Utils.isNullorEmpty(o2))
			return 0;
		//TODO ACTUALLY ALIGN consonants here
		double alignmentScore = 0;
		List<ConsonantPhoneme> shortest = o1;
		List<ConsonantPhoneme> longest = o2;
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

	private double score2Vowels(VowelPhoneme n1, VowelPhoneme n2) {
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

	private double getDistance(double[] p1, double[] p2) {
		double d = Math.sqrt(Math.pow(p2[0] - p1[0], 2) + Math.pow(p2[1] - p1[1], 2));
		return d;
	}

	private double score2Consonants(ConsonantPhoneme ph1, ConsonantPhoneme ph2) {
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
			score += voicingWeight;
		if (m1 == m2)
			score += mannerOfArticulationWeight;
		if (p1 == p2)
			score += placeOfArticulationWeight;
		return score;
	}

}
