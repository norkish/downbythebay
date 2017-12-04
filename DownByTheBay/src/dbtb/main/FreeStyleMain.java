package dbtb.main;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dbtb.constraint.AbsoluteStressConstraint;
import dbtb.constraint.BinaryRhymeConstraint;
import dbtb.constraint.ConditionedConstraint;
import dbtb.constraint.EndOfWordConstraint;
import dbtb.constraint.FloatingConstraint;
import dbtb.constraint.SemanticMeaningConstraint;
import dbtb.constraint.StartOfWordConstraint;
import dbtb.data.DataLoader;
import dbtb.data.SyllableToken;
import dbtb.data.DataLoader.DataSummary;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderNHMMMultiThreaded;
import dbtb.markov.Token;
import dbtb.markov.UnsatisfiableConstraintSetException;

public class FreeStyleMain {

	static int markovOrder;
	
	public static String rootPath = "";
	
	private static final Random rand = new Random();
	private static final int LINE1_LEN = rand.nextInt(2)+2; 
	private static final int LINE2_LEN = LINE1_LEN;
	private static final int LINE3_LEN = rand.nextInt(2)+2;
	private static final int LINE4_LEN = LINE3_LEN;
	private static final int TOT_LEN = LINE1_LEN + LINE2_LEN + LINE3_LEN + LINE4_LEN;
	
	public static void main(String[] args) throws InterruptedException{

		setupRootPath();

		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<>();
		for (int i = 0; i < TOT_LEN; i++) {
			final ArrayList<ConditionedConstraint<SyllableToken>> constraintsForPosition = new ArrayList<>();
			constraints.add(constraintsForPosition);
		}
		
		markovOrder = Math.max(LINE2_LEN, LINE4_LEN);
		// train a high-order markov model on a corpus
		constraints.get(0).add(new ConditionedConstraint<>(new StartOfWordConstraint<>()));
		constraints.get(LINE1_LEN-1).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get((LINE1_LEN+LINE2_LEN)-1).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get((LINE1_LEN+LINE2_LEN+LINE3_LEN)-1).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get((LINE1_LEN+LINE2_LEN+LINE3_LEN+LINE4_LEN)-1).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));

		constraints.get((LINE1_LEN+LINE2_LEN)-1).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(LINE2_LEN)));
		constraints.get((LINE1_LEN+LINE2_LEN+LINE3_LEN+LINE4_LEN)-1).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(LINE4_LEN)));

		for(Integer position: new Integer[]{LINE1_LEN-1,(LINE1_LEN+LINE2_LEN)-1,(LINE1_LEN+LINE2_LEN+LINE3_LEN)-1,(LINE1_LEN+LINE2_LEN+LINE3_LEN+LINE4_LEN)-1})
			constraints.get(position).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		
		DataLoader dl = new DataLoader(markovOrder);
		DataSummary summary = dl.loadData();
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
			SparseVariableOrderNHMMMultiThreaded<SyllableToken> constrainedMarkovModel = new SparseVariableOrderNHMMMultiThreaded<>(markovModel, TOT_LEN, constraints);
			System.out.println();

			for (int i = 0; i < 8; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<SyllableToken> generatedSequence = constrainedMarkovModel.generate(TOT_LEN);
				// convert the sequence of syllable tokens to a human-readable string
				for (int j = 0; j < generatedSequence.size(); j++) {
					SyllableToken syllableToken = generatedSequence.get(j);
					if (j == LINE1_LEN || j == (LINE1_LEN*2) || j == (LINE1_LEN*2 + LINE3_LEN)) System.out.println();
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
		FreeStyleMain.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
