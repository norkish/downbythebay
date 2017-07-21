package main;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import constraint.ConditionedConstraint;
import constraint.EndOfWordConstraint;
import constraint.FloatingConstraint;
import constraint.SemanticMeaningConstraint;
import constraint.StartOfWordConstraint;
import data.DataLoader;
import data.DataLoader.DataSummary;
import data.SyllableToken;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMM;
import markov.Token;
import markov.UnsatisfiableConstraintSetException;

public class HaikuMain {

	static int markovOrder;
	
	public static String rootPath = "";
	
	private static final int LINE1_LEN = 5; 
	private static final int LINE2_LEN = 7;
	private static final int LINE3_LEN = 5;
	private static final int HAIKU_LEN = LINE1_LEN + LINE2_LEN + LINE3_LEN;
	
	public static void main(String[] args) throws InterruptedException{

		setupRootPath();

		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<>();
		for (int i = 0; i < HAIKU_LEN; i++) {
			final ArrayList<ConditionedConstraint<SyllableToken>> constraintsForPosition = new ArrayList<>();
			constraints.add(constraintsForPosition);
		}
		
		markovOrder = 4;
		// train a high-order markov model on a corpus
		constraints.get(0).add(new ConditionedConstraint<>(new StartOfWordConstraint<>()));
		constraints.get(4).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(5).add(new ConditionedConstraint<>(new FloatingConstraint<>(markovOrder, 
				new SemanticMeaningConstraint<>("nature"))));
		constraints.get(11).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
//		constraints.get(11).add(new ConditionedConstraint<>(new FloatingConstraint<>(markovOrder, 
//				new SemanticMeaningConstraint<>("nature"))));
		constraints.get(16).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
//		constraints.get(16).add(new ConditionedConstraint<>(new FloatingConstraint<>(markovOrder, 
//				new SemanticMeaningConstraint<>("nature"))));
		
		
		DataLoader dl = new DataLoader(markovOrder);
		DataSummary summary = dl.loadData();
		System.out.println("Data loaded for HaikuMain.java");
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.priors, summary.transitions);
		System.out.println("Creating Markov Model");

		// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
		try {
			System.out.println("Creating " + markovOrder + "-order NHMM of length " + HAIKU_LEN + " with constraints:");
			for (int i = 0; i < constraints.size(); i++) {
				System.out.println("\tAt position " + i + ":");
				for (ConditionedConstraint<SyllableToken> constraint : constraints.get(i)) {
					System.out.println("\t\t" + constraint);
				}
			}
			SparseVariableOrderNHMM<SyllableToken> constrainedMarkovModel = new SparseVariableOrderNHMM<>(markovModel, HAIKU_LEN, constraints);
			System.out.println();

			for (int i = 0; i < 8; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<SyllableToken> generatedSequence = constrainedMarkovModel.generate(HAIKU_LEN);
				// convert the sequence of syllable tokens to a human-readable string
				for (int j = 0; j < generatedSequence.size(); j++) {
					SyllableToken syllableToken = generatedSequence.get(j);
					if (j == 5 || j == 12) System.out.println();
					System.out.print(syllableToken.getStringRepresentationIfFirstSyllable() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":""));
				}
				System.out.println("\nProb:" + constrainedMarkovModel.probabilityOfSequence(generatedSequence.toArray(new Token[0])));
				System.out.println();
			}
		} catch (UnsatisfiableConstraintSetException e) {
			System.out.println("\t" + e.getMessage());
		}
	}

	public static void setupRootPath() {
		//Set the root path of Lyrist in U
		final File currentDirFile = new File("");
		HaikuMain.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
