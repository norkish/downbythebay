package dbtb.main;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.StopWatch;

import dbtb.constraint.ConditionedConstraint;
import dbtb.constraint.PartsOfSpeechConstraint;
import dbtb.constraint.StartsWithLetterConstraint;
import dbtb.data.WordDataLoader;
import dbtb.data.WordDataLoader.DataSummary;
import dbtb.data.WordToken;
import dbtb.linguistic.syntactic.Pos;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderNHMMMultiThreaded;
import dbtb.markov.UnsatisfiableConstraintSetException;

public class MnemonicGenerator {

	static int markovOrder = 2;
	
	public static String rootPath = "/Users/bodipaul/Archive/2017_BYU/ComputationalCreativity/";
	
	public static void setRootPath(String newRootPath) {
		rootPath = newRootPath;
	}
	
	public static void main(String[] args) throws InterruptedException{

		setupRootPath();
		
		if (args.length > 0) {
			WordDataLoader.trainingSource = args[0];
		}

		Map<String, String> problems = new HashMap<String, String>();
		problems.put("Dante’s 9 circles of hell", "Limbo, Lust, Gluttony, Greed, Anger, Heresy, Violence, Fraud, Treachery");
		problems.put("Last 10 winners of the FIFA World Cup", "France, Germany, Spain, Italy, Brazil, France, Brazil, West Germany, Argentina, Italy");
		problems.put("Name the first 10 US presidents","Washington, Adams, Jefferson, Madison, Monroe, Adams, Jackson, Buren, Harrison, Tyler");
		problems.put("Geological time scale", "Cambrian, Ordovician, Silurian, Devonian, Mississippian, Pennsylvanian, Permain, Triassic, Jurassic, Cretaceous, Tertiary");
		problems.put("Locations of the first 9 ICCC conferences", "Lisbon, Mexico City, Dublin, Sydney, Ljubljana, Park City, Paris, Atlanta, Salamanca");
		problems.put("11 Pauline Epistles", "Romans, Corinthians, Galatians, Ephesians, Philippians, Colossians, Thessalonians, Hebrews, Timothy, Titus, Philemon");
		problems.put("10 Buddhist perfections (virtues)", "Dana, Sila, Nekkhamma, Panna, Viriya, Khanti, Sacca, Adhitthana, Metta, Upekkha");
		problems.put("Myanmar zodiac signs", "Garuda, Tiger, Lion, Tusked Elephant, Elephant, Rat, Guinea Pig, Naga");
//		problems.put("Mathematical Operations", "Please, Excuse My dear aunt sally");
		problems.put("Four stages of enlightenment", "Stream-enterer, Once-returner, Non-returner, Arahant");
		problems.put("Command hierarchy of armies", "Field Marshal, General, Brigadier, Colonel, Major, Captain, Lieutenant, Officer Cadet");
		problems.put("Stages of grief", "Denial, Anger, Bargaining, Depression, Acceptance");
		problems.put("Basic virtues related to Erik Erikson’s Stages of Psychosocial Development", "Hope, Will, Purpose, Competency, Fidelity, Love, Care, Wisdom");
		problems.put("Levels of organization", "Biosphere, Ecosystem, Community, Population, Organism, Organ System, Organ, Tissue, Cell, Molecule");
		problems.put("Alkali metals", "Lithium, Sodium, Potassium, Rubidium, Caesium, Francium");
		problems.put("Alkaline earth metals", "Beryllium, Magnesium, Calcium, Strontium, Barium, Radium");
		problems.put("Cell cycle", "Interphase, Prophase, Prometaphase, Metaphase, Anaphase, Telophase, Cytokinesis");
		problems.put("Presidents on ascending dollar bill denominations in the United States up to $500", "Washington, Jefferson, Lincoln, Hamilton, Jackson, Grant, Franklin, McKinley");
		problems.put("Regions of the Brain", "Frontal, parietal, occipital, temporal, cerebellum, brainstem");
		problems.put("1992 Dream Team Starting Lineup", "Ewing, Barkley, Bird, Jordan, Johnson");
		problems.put("Title of our paper","Constrained Probabilistic Modeling for Computational Creativity");

		
		SparseVariableOrderMarkovModel<WordToken> markovModel = null;
		
		StopWatch watch = new StopWatch();
		watch.start();
		WordDataLoader dl = new WordDataLoader(markovOrder);
		DataSummary summary = dl.loadData();
//			System.out.println("Data loaded for Main.java");
		watch.stop();
//			System.out.println("Time to train on data:" + watch.getTime());
		watch.reset();
		markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.priors, summary.transitions);

		final HashSet<Pos> disallowedPosAtPhraseEnd = new HashSet<Pos>(Arrays.asList(Pos.DT, Pos.IN, Pos.CC, Pos.TO, Pos.PRP$, Pos.WP$, Pos.WRB));
		
		for (String descriptor : problems.keySet()) {
			System.out.println();
			System.out.println("NEW PROBLEM: " + descriptor + ":");
			String[] problem = problems.get(descriptor).split(", ");
			System.out.println("(" + Arrays.toString(problem) + ")");
			// a constraint is a {syllable position, feature index, value}
			List<List<ConditionedConstraint<WordToken>>> generalConstraints = new ArrayList<>();
			for (int i = 0; i < problem.length; i++) {
				final ArrayList<ConditionedConstraint<WordToken>> constraintsForPosition = new ArrayList<>();
				constraintsForPosition.add(new ConditionedConstraint<>(new StartsWithLetterConstraint<>(problem[i].charAt(0))));
				if (i == problem.length-1) {
					constraintsForPosition.add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(disallowedPosAtPhraseEnd), false));
				}
				
				generalConstraints.add(constraintsForPosition);
			}
			
//			System.out.println("Creating Markov Model");

			// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
			SparseVariableOrderNHMMMultiThreaded<WordToken> constrainedMarkovModel;
			watch.start();
			try {
				System.out.println("Creating " + markovOrder + "-order NHMM of length " + problem.length + " with constraints:");
//				for (int i = 0; i < generalConstraints.size(); i++) {
//					System.out.println("\tAt position " + i + ":");
//					for (ConditionedConstraint<WordToken> constraint : generalConstraints.get(i)) {
//						System.out.println("\t\t" + constraint);
//					}
//				}
				constrainedMarkovModel = new SparseVariableOrderNHMMMultiThreaded<WordToken>(markovModel, problem.length, generalConstraints);
				memoryCheck();

				System.out.println();
			} catch (UnsatisfiableConstraintSetException e) {
				System.out.println("\t" + e.getMessage());
				watch.stop();
				watch.reset();
//				System.out.println("Time to build model:" + watch.getTime());
				continue;
			}
			watch.stop();
			watch.reset();

//			System.out.println("Time to build model:" + watch.getTime());
			
			System.out.println("Finished creating " + markovOrder + "-order NHMM of length " + problem.length + " with constraints:");
//			for (int i = 0; i < generalConstraints.size(); i++) {
//				System.out.println("\tAt position " + i + ":");
//				for (ConditionedConstraint<WordToken> constraint : generalConstraints.get(i)) {
//					System.out.println("\t\t" + constraint);
//				}
//			}
			
			for (int i = 0; i < 100; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<WordToken> generatedSequence = constrainedMarkovModel.generate(problem.length);
//			for(List<WordToken> generatedSequence : constrainedMarkovModel.generateFromAllPriors(problem.length)) {
				// convert the sequence of syllable tokens to a human-readable string
				for (WordToken syllableToken : generatedSequence) {
					System.out.print(syllableToken.getStringRepresentation() + " ");
				}
				System.out.println();//"\t\tProb:" + constrainedMarkovModel.probabilityOfSequence(generatedSequence.toArray(new Token[0])));
			}
		}
	}

	public static void memoryCheck() {
		System.out.println("MEMORY CHECK: " + computePercentTotalMemoryUsed() + "% used");
	}

	public static double computePercentTotalMemoryUsed() {
		return (100.0*(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))/Runtime.getRuntime().maxMemory();
	}

	public static void setupRootPath() {
		//Set the root path of Lyrist in U
		final File currentDirFile = new File("");
		MnemonicGenerator.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
