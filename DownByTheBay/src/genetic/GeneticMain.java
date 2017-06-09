package genetic;

import java.util.*;

public class GeneticMain {

	public static Random r = new Random();
	private final static int topIndividualN = 10;
	private final static int offspringN = 100;
	//rhyme score threshold is anything greater than 0, up to 1.0. Anything 0 to -1 is a bad rhyme

	public static void main(String[] args) {
		Map<String, Double> values = new HashMap<>();

		values.put("frontness", 1.0);
		values.put("height", 1.0);

		values.put("place_of_articulation", 1.0);
		values.put("manner_of_articulation", 1.0);
		values.put("voicing", 1.0);

		values.put("onset", 1.0);
		values.put("nucleus", 1.0);
		values.put("coda", 1.0);

		TreeSet<Individual> topIndividuals = new TreeSet<>();
		for (int i = 0; i < topIndividualN; i++) {
			Individual temp = new Individual(values);
			temp.mutate();
		}

		double highestFitness = -1;
		int generation = 0;
		final int maxGenerations = 1000000000;

		while (highestFitness < 1.0 && generation < maxGenerations) {
			generation++;

			//top individuals mate
			List<Individual> allIndividualsOfNewGeneration = mateTopIndividuals(topIndividuals);

			//find top topIndividualN individuals of new generation
			topIndividuals = findTopIndividuals(allIndividualsOfNewGeneration);

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
	/*
Vowel attribs
	Frontness weight
	Height weight
Consonant attribs
	Place of articulation weight
	Manner of articulation weight
	Voicing weight
Syllable attribs
	Onset weight
	Nucleus weight
	Coda weight

	 */

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
			double score = calculateFitness(i);
			i.setFitness(score);
		}
		return result;
	}

	public static double calculateFitness(Individual ind1) {
		IndividualResults results = classifyIndividual(ind1);
		double precision = calculatePrecision(results.truePositives, results.falsePositives);
		double recall = calculateRecall(results.truePositives, results.falseNegatives);
		double fScore = calculateFScore(precision, recall);
		return fScore;
	}

	public static IndividualResults classifyIndividual(Individual ind1) {
		//dictionary is all words in CMU pronouncing dictionary
		//for each word in dictionary:
			// if it contains syllable w/ an onset or coda of length > 1, continue
			// count all words Datamuse says rhyme with it -> positives (rhymes)
			// count all other words in dictionary -> negatives (non-rhymes)
			// count all words (last syllable) my algorithm says rhymes with it -> (if rhyme, true positive. if non-rhyme, false positive)
			// count all other words in dictionary -> (if rhyme, false negative. if non-rhyme, true negative)

		return null;
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
		return 2 * (precision * recall) / (precision + recall);
	}

	public static double calculatePrecision(double truePositives, double falsePositives) {
		return truePositives / (truePositives + falsePositives);
	}

	public static double calculateRecall(double truePositives, double falseNegatives) {
		return truePositives / (falseNegatives + truePositives);
	}

}
