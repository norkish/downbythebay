package genetic;

import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.syllabic.Rhymer;
import linguistic.phonetic.syllabic.WordSyllables;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Individual implements Comparable<Individual> {

	private static int count = 0;

	private Map<String,Double> values;
	private double fitness = -1;
	public int id = 0;

//	public static void main(String[] args) {
//		Individual test = new Individual();
//	}

	public Individual(Map<String, Double> values) {
		this.values = values;
		id = count++;
	}

	public Individual(Individual ind) {
		this.values = ind.getValues();
		id = count++;
	}

	public void mutate() {
		for (Map.Entry<String,Double> entry : this.getValues().entrySet()) {
			if (GeneticMain.r.nextBoolean()) {
				entry.setValue(entry.getValue() + (GeneticMain.r.nextDouble() - 0.5));
				if (entry.getValue() <= 0) {
					entry.setValue(0d);
				}
			}
		}
	}

	public Individual crossover(Individual mate) {
		Individual offspring = new Individual(this);
		offspring.setValues(this.getValues());
		for (Map.Entry<String,Double> entry : this.getValues().entrySet()) {
			if (GeneticMain.r.nextBoolean()) {
				offspring.getValues().put(entry.getKey(), mate.getValues().get(entry.getKey()));
			}
		}
		return offspring;
	}

	public Map<String, Double> getValues() {
		return values;
	}

	public void setValues(Map<String, Double> values) {
		this.values = values;
	}

	public double getFitness() {
		return fitness;
	}

	public void setFitness(double fitness) {
		this.fitness = fitness;
	}

	@Override
	public int compareTo(Individual o) {
		if (this.fitness > o.fitness) return 1;
		else if (this.fitness < o.fitness) return -1;
		else {
			if (this.id < o.id) return 1;
			else if (this.id > o.id) return -1;
			return 0;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Individual that = (Individual) o;

		if (Double.compare(that.getFitness(), getFitness()) != 0) return false;
		if (id != that.id) return false;
		return getValues().equals(that.getValues());
	}

	@Override
	public int hashCode() {
		int result;
		long temp;
		result = getValues().hashCode();
		temp = Double.doubleToLongBits(getFitness());
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		result = 31 * result + id;
		return result;
	}

	public IndividualResults classify() {
		int truePositives = 0;
		int trueNegatives = 0;
		int falsePositives = 0;
		int falseNegatives = 0;
		for (Map.Entry<String, WordSyllables> testDictWord : RhymeZoneApiInterface.dictionary.entrySet()) {
			Set<String> positives = RhymeZoneApiInterface.getStrings(RhymeZoneApiInterface.rhymeZoneRhymes.get(testDictWord.getKey().toLowerCase()));//get all words datamuse says rhyme with it removing ones outside of valid cmu dictionary
			Set<String> negatives = new HashSet<>(RhymeZoneApiInterface.dictionary.keySet());//TODO idea: randomly shorten negatives list to the size of positives for speed-up?
			negatives.removeAll(positives);

			Rhymer temp = new Rhymer(this);
			Set<String> tempTruePositives = new HashSet<>();
			Set<String> tempTrueNegatives = new HashSet<>();
			Set<String> tempFalsePositives = new HashSet<>();
			Set<String> tempFalseNegatives = new HashSet<>();
			for (String positive : positives) {
				List<WordSyllables> positivePronunciations = Phoneticizer.getSyllables(positive);
				if (positivePronunciations == null || positivePronunciations.isEmpty()) continue;
				WordSyllables positivePronunciation = positivePronunciations.get(0);
				//only test positives w/ same # syllables
				if (positivePronunciation.size() != testDictWord.getValue().size()) continue;
				double wordsScore = 0;
				for (int s = 0; s < positivePronunciation.size(); s++) {
					double syllablesScore = temp.score2Syllables(positivePronunciation.get(s), testDictWord.getValue().get(s));
					wordsScore += syllablesScore;
				}
				wordsScore /= positivePronunciation.size();
				if (wordsScore >= GeneticMain.fitnessThreshold)
					tempTruePositives.add(positive);
				else
					tempFalsePositives.add(positive);
			}
			for (String negative : negatives) {
				List<WordSyllables> negativePronunciations = Phoneticizer.getSyllables(negative);
				if (negativePronunciations == null || negativePronunciations.isEmpty()) continue;
				WordSyllables negativePronunciation = negativePronunciations.get(0);
				//only test negatives w/ same # syllables
				if (negativePronunciation.size() != testDictWord.getValue().size()) continue;
				double wordsScore = 0;
				for (int s = 0; s < negativePronunciation.size(); s++) {
					double syllablesScore = temp.score2Syllables(negativePronunciation.get(s), testDictWord.getValue().get(s));
					wordsScore += syllablesScore;
				}
				wordsScore /= negativePronunciation.size();
				if (wordsScore >= GeneticMain.fitnessThreshold)
					tempFalseNegatives.add(negative);
				else
					tempTrueNegatives.add(negative);
			}
			//inspect results here

			truePositives += tempTruePositives.size();
			trueNegatives += tempTrueNegatives.size();
			falsePositives += tempFalsePositives.size();
			falseNegatives += tempFalseNegatives.size();
		}
		return new IndividualResults(truePositives, trueNegatives, falsePositives, falseNegatives);
	}

	public double calculateFitness() {
		IndividualResults results = this.classify();
		double precision = calculatePrecision(results.truePositives, results.falsePositives);
		double recall = calculateRecall(results.truePositives, results.falseNegatives);
		double fScore = calculateFScore(precision, recall);
		this.setFitness(fScore);
		return fScore;
	}

	public static double calculateFScore(double precision, double recall) {
		return 2 * ((precision * recall) / (precision + recall));
	}

	public static double calculatePrecision(double truePositives, double falsePositives) {
		return truePositives / (truePositives + falsePositives);
	}

	public static double calculateRecall(double truePositives, double falseNegatives) {
		return truePositives / (falseNegatives + truePositives);
	}

}
