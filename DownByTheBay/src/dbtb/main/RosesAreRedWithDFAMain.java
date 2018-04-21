package dbtb.main;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import automaton.Automaton;
import automaton.MatchDFABuilderDFS;
import automaton.RegularConstraintApplier;
import automaton.RegularConstraintApplier.StateToken;
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

public class RosesAreRedWithDFAMain {

	static int markovOrder;
	
	public static String rootPath = "";
	
	private static final int LINE1_LEN = 4; 
	private static final int LINE2_LEN = 4;
	private static final int LINE3_LEN = 4;
	private static final int LINE4_LEN = 4;
	private static final int TOT_LEN = LINE1_LEN + LINE2_LEN + LINE3_LEN + LINE4_LEN;
	
	public static void main(String[] args) throws InterruptedException{

		setupRootPath();

		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<>();
		for (int i = 0; i < TOT_LEN; i++) {
			final ArrayList<ConditionedConstraint<StateToken<SyllableToken>>> constraintsForPosition = new ArrayList<>();
			constraints.add(constraintsForPosition);
		}
		
		markovOrder = 1;
		// train a high-order markov model on a corpus
		constraints.get(0).add(new ConditionedConstraint<>(new StartOfWordConstraint<>()));
		constraints.get(3).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(7).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(11).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(15).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
//		constraints.get(15).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(8)));

		constraints.get(0).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(3).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(4).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(7).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(8).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(11).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(13).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		constraints.get(15).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(1)));
		
		DataLoader dl = new DataLoader(markovOrder);
		DataSummary summary = dl.loadData();
		System.out.println("Data loaded for RosesAreREdWithDFAMain.java");
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.priors, summary.transitions);
		System.out.println("Creating Markov Model");

		// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
		try {
			System.out.println("Creating " + markovOrder + "-order NHMM of length " + TOT_LEN + " with constraints:");
			for (int i = 0; i < constraints.size(); i++) {
				System.out.println("\tAt position " + i + ":");
				for (ConditionedConstraint<StateToken<SyllableToken>> constraint : constraints.get(i)) {
					System.out.println("\t\t" + constraint);
				}
			}
			
			int[] matchConstraintList = new int[]{-1,-1,-1,-1,-1,-1,-1,16,-1,-1,-1,-1,-1,-1,-1,-1}; // 1-based
			int length = matchConstraintList.length;
			boolean[] matchConstraintOutcomeList = new boolean[length];
			Arrays.fill(matchConstraintOutcomeList, true);
			
			Automaton<SyllableToken> A = MatchDFABuilderDFS.buildEfficiently(matchConstraintList,matchConstraintOutcomeList, M);
			
			SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> constrainedMarkovModel = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
			
//			SparseVariableOrderNHMMMultiThreaded<SyllableToken> constrainedMarkovModel = new SparseVariableOrderNHMMMultiThreaded<>(M, TOT_LEN, constraints);
			System.out.println();

			for (int i = 0; i < 8; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<StateToken<SyllableToken>> generatedSequence = constrainedMarkovModel.generate(TOT_LEN);
				// convert the sequence of syllable tokens to a human-readable string
				for (int j = 0; j < generatedSequence.size(); j++) {
					SyllableToken syllableToken = generatedSequence.get(j).token;
					if (j % 4 == 0) System.out.println();
					System.out.print(syllableToken.getStringRepresentationIfFirstSyllable() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":""));
				}
				for (int j = 0; j < generatedSequence.size(); j++) {
					SyllableToken syllableToken = generatedSequence.get(j).token;
					if (j % 4 == 0) System.out.println();
					System.out.print(syllableToken + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":""));
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
		RosesAreRedWithDFAMain.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
