package main;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import constraint.BinaryRhymeConstraint;
import constraint.ConditionedConstraint;
import constraint.EndOfWordConstraint;
import constraint.FloatingConstraint;
import constraint.SemanticMeaningConstraint;
import constraint.StartOfWordConstraint;
import constraint.AbsoluteStressConstraint;
import data.DataLoader;
import data.DataLoader.DataSummary;
import data.SyllableToken;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMM;
import markov.Token;
import markov.UnsatisfiableConstraintSetException;

public class RosesAreRedMain {

	static int markovOrder;
	
	public static String rootPath = "";
	
	private static final int LINE1_LEN = 4; 
	private static final int LINE2_LEN = 4;
	private static final int LINE3_LEN = 4;
	private static final int LINE4_LEN = 4;
	private static final int TOT_LEN = LINE1_LEN + LINE2_LEN + LINE3_LEN + LINE4_LEN;
	
	public static void main(String[] args){

		setupRootPath();

		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<>();
		for (int i = 0; i < TOT_LEN; i++) {
			final ArrayList<ConditionedConstraint<SyllableToken>> constraintsForPosition = new ArrayList<>();
			constraints.add(constraintsForPosition);
		}
		
		markovOrder = 8;
		// train a high-order markov model on a corpus
		constraints.get(0).add(new ConditionedConstraint<>(new StartOfWordConstraint<>()));
		constraints.get(3).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(7).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(11).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(15).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(15).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(8)));

		constraints.get(0).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(3).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(4).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(7).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(8).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(11).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(13).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(15).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		
		DataSummary summary = DataLoader.loadData(markovOrder);
		System.out.println("Data loaded for HaikuMain.java");
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.priors, summary.transitions);
		System.out.println("Creating Markov Model");

		// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
		try {
			System.out.println("Creating " + markovOrder + "-order NHMM of length " + TOT_LEN + " with constraints:");
			for (int i = 0; i < constraints.size(); i++) {
				System.out.println("\tAt position " + i + ":");
				for (ConditionedConstraint<SyllableToken> constraint : constraints.get(i)) {
					System.out.println("\t\t" + constraint);
				}
			}
			SparseVariableOrderNHMM<SyllableToken> constrainedMarkovModel = new SparseVariableOrderNHMM<>(markovModel, TOT_LEN, constraints);
			System.out.println();

			for (int i = 0; i < 8; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<SyllableToken> generatedSequence = constrainedMarkovModel.generate(TOT_LEN);
				// convert the sequence of syllable tokens to a human-readable string
				for (int j = 0; j < generatedSequence.size(); j++) {
					SyllableToken syllableToken = generatedSequence.get(j);
					if (j % 4 == 0) System.out.println();
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
		RosesAreRedMain.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
