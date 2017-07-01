package genetic;

import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.syllabic.Rhymer;
import linguistic.phonetic.syllabic.Syllable;
import linguistic.phonetic.syllabic.WordSyllables;

import java.util.*;

public class GeneticMain {

	public static Random r = new Random();
	private final static int topIndividualN = 10;
	private final static int offspringN = 100;
	private final static int maxGenerations = 1000000000;

	public static void main(String[] args) {
		Map<String, Double> values = new HashMap<>();

		values.put("frontness", 1.0);
		values.put("height", 1.0);

		values.put("place_of_articulation", 100.0);
		values.put("manner_of_articulation", 100.0);
		values.put("voicing", 100.0);

		values.put("onset", 1.0);
		values.put("nucleus", 1.0);
		values.put("coda", 1.0);

		values.put("stress_diff", 1.0);

		TreeSet<Individual> topIndividuals = new TreeSet<>();
		for (int i = 0; i < topIndividualN; i++) {
			Individual temp = new Individual(values);
			temp.mutate();
		}

		double highestFitness = -1;
		int generation = 0;

		while (highestFitness < 1.0 && generation < maxGenerations) {
			generation++;

			//top individuals mate
			List<Individual> allIndividualsOfNewGeneration = mateTopIndividuals(topIndividuals);

			//OPTIONAL -- mix parents and new generation
			List<Individual> pool = new ArrayList<>();
			pool.addAll(allIndividualsOfNewGeneration);
			pool.addAll(topIndividuals);

			//find top topIndividualN individuals of new generation
			topIndividuals = findTopIndividuals(pool);

			//update highestFitness
			highestFitness = topIndividuals.last().getFitness();
		}
		System.out.println("Generations: " + generation);
		System.out.println("Best fitness: " + highestFitness);
		System.out.println("Values: " + highestFitness);
		for (Map.Entry<String,Double> entry : topIndividuals.last().getValues().entrySet()) {
			System.out.println("\t" + entry.getKey() + ": " + entry.getValue());
		}

	}

	public static List<Individual> mateTopIndividuals(Collection<Individual> topIndividuals) {
		//TODO optimize so only lists come in? or they stay as treesets for sorting?
		List<Individual> result = new ArrayList<>();
		for (int i = 0; i > offspringN; i++) {
			int n1 = r.nextInt(topIndividualN);
			int n2 = n1;
			while (n2 == n1) {
				n2 = r.nextInt(topIndividualN);
			}
			Individual mater1 = null;
			int j = 0;
			for (Individual individual : topIndividuals) {
				if (j == n1) {
					mater1 = individual;
					break;
				}
				j++;
			}
			Individual mater2 = null;
			j = 0;
			for (Individual individual : topIndividuals) {
				if (j == n2) {
					mater2 = individual;
					break;
				}
				j++;
			}
			Individual child = crossover(mater1, mater2);
			child.mutate();
			result.add(child);
		}
		return result;
	}

	public static TreeSet<Individual> findTopIndividuals(Collection<Individual> allIndividuals) {
		TreeSet<Individual> result = new TreeSet<>();
		//sort by fitness, return the top topIndividualN
		for (Individual i : allIndividuals) {
			double fitnessScore = calculateFitness(i);
			i.setFitness(fitnessScore);
		}
		return result;
	}

	public static double calculateFitness(Individual ind) {
		IndividualResults results = classifyIndividual(ind);
		double precision = calculatePrecision(results.truePositives, results.falsePositives);
		double recall = calculateRecall(results.truePositives, results.falseNegatives);
		double fScore = calculateFScore(precision, recall);
		return fScore;
	}

	public static IndividualResults classifyIndividual(Individual ind) {
		Map<String, List<WordSyllables>> dictionary = new HashMap<>(Phoneticizer.syllableDict);
//		dictionary.keySet().retainAll(valid-cmu-words); // not necessary if using updated cmu dictionary
		int truePositives = 0;
		int trueNegatives = 0;
		int falsePositives = 0;
		int falseNegatives = 0;
		for (Map.Entry<String, List<WordSyllables>> entry : dictionary.entrySet()) {
			for (WordSyllables pronunciation : entry.getValue()) {
				boolean pronunciationIsValid = true;
				for (Syllable syllable : pronunciation) {
					if (syllable.getOnset().size() > 1 || syllable.getCoda().size() > 1) {
						pronunciationIsValid = false;
						break;
					}
				}
				if (pronunciationIsValid) {
					Set<String> positives = null;//get all words datamuse says rhyme with it removing ones outside of valid cmu dictionary
					Set<String> negatives = new HashSet<>(dictionary.keySet());
					negatives.removeAll(negatives);

					Rhymer temp = new Rhymer(ind);
					Set<String> tempTruePositives = new HashSet<>();
					Set<String> tempTrueNegatives = new HashSet<>();
					Set<String> tempFalsePositives = new HashSet<>();
					Set<String> tempFalseNegatives = new HashSet<>();
					for (String positive : positives) {
						double score = 0;// = temp.score2Syllables();
						if (score > 0)
							tempTruePositives.add(positive);
						else
							tempFalsePositives.add(positive);
					}
					for (String negative : negatives) {
						double score = 0;// = temp.score2Syllables();
						if (score > 0)
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
			}
		}
		//for each word in dictionary:
			// if it contains syllable w/ an onset or coda of length > 1, continue
			// count all words Datamuse says rhyme with it -> positives (rhymes)
			// count all other words in dictionary -> negatives (non-rhymes)
			// count all words (last syllable) my algorithm says rhymes with it -> (if rhyme, true positive. if non-rhyme, false positive)
			// count all other words in dictionary -> (if rhyme, false negative. if non-rhyme, true negative)

		return new IndividualResults(truePositives, trueNegatives, falsePositives, falseNegatives);
	}

	public static Individual crossover(Individual ind1, Individual ind2) {
		Individual ind3 = new Individual(ind1);
		ind3.setValues(ind1.getValues());

		for (Map.Entry<String,Double> entry : ind1.getValues().entrySet()) {
			if (r.nextBoolean()) {
				ind3.getValues().put(entry.getKey(),ind2.getValues().get(entry.getKey()));
			}
		}
		return ind3;
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
