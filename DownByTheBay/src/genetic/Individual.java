package genetic;

import java.util.Map;

public class Individual implements Comparable<Individual> {

	private Map<String,Double> values;
	private double fitness = -1;

	public Individual(Map<String, Double> values) {
		this.values = values;
	}

	public Individual(Individual ind) {
		this.values = ind.getValues();
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
		else return 0;
	}
}
