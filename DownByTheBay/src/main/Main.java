package main;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;

import constraint.BinaryRhymeConstraint;
import constraint.ConditionedConstraint;
import constraint.EndOfWordConstraint;
import constraint.FloatingConstraint;
import constraint.PartsOfSpeechConstraint;
import constraint.StartOfWordConstraint;
import constraint.StressConstraint;
import data.DataLoader;
import data.DataLoader.DataSummary;
import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMM;
import markov.Token;
import markov.UnsatisfiableConstraintSetException;

public class Main {

	static int markovOrder;
	
//	final static int HAVE = 0, YOU = 1, EV = 2, ER = 3, SEEN = 4, 
		final static int A = 0, LLA = 1, MA = 2,
			WEAR = 3, ING = 4, POL = 5, KA = 6, DOT = 7, PA = 8, JA = 9, MAS = 10;

	public static String rootPath = "";
	
	public static void main(String[] args){

		setupRootPath();

		int[] rhythmicSuperTemplate = new int[]{0,1,0,1,0,1,0,1,0,1,0};
		
		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<SyllableToken>>> generalConstraints = new ArrayList<>();
		for (int i = 0; i < rhythmicSuperTemplate.length; i++) {
			final ArrayList<ConditionedConstraint<SyllableToken>> constraintsForPosition = new ArrayList<>();
			generalConstraints.add(constraintsForPosition);
			constraintsForPosition.add(new ConditionedConstraint<>(new StressConstraint<>(rhythmicSuperTemplate[i])));
		}
		
		// Add rest of constraints
		generalConstraints.get(A).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.DT, Pos.NN, Pos.JJ)))));
		generalConstraints.get(LLA).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS)))));
		generalConstraints.get(JA).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS)))));
		generalConstraints.get(MAS).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS)))));
		
		// train a high-order markov model on a corpus
		
		int[][] allRhythmicTemplates = new int[][] {
			new int[]{0,1,-1,-1,-1,1,0,-1,0,1,-1}, // "a bear . . . combing . his hair ."
			new int[]{0,1,-1,-1,-1,1,0,1,0,1,-1}, // "a ma . . . drinking from a straw ."
			new int[]{0,1,0,-1,-1,1,0,-1,0,1,0}, // "a llama wearing pajamas"
			new int[]{0,1,-1,1,0,1,0,1,-1,1,-1}, //"a moose . with a pair of new . shoes ."
			new int[]{0,1,0,1,0,1,0,1,0,1,0}, // "a llama wearing polka dot pajamas"
		};
		
		int prevOrder = -1;
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = null;
		for (int[] rhythmicTemplate : allRhythmicTemplates) {
			List<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<>();
			for (List<ConditionedConstraint<SyllableToken>> allConstraintsAtPosition : generalConstraints) {
				constraints.add(new ArrayList<>(allConstraintsAtPosition));
			}
			int templateLength = 0;
			int rhymeDistance = 0;
			
			int prevStress = -1;
			for (int i = 0; i < rhythmicTemplate.length; i++) {
				int stress = rhythmicTemplate[i];
				if (stress == -1){
					constraints.remove(templateLength);
					if (prevStress != -1) constraints.get(templateLength-1).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
					prevStress = stress;
					continue;
				}
				
				if (i > LLA && i <= JA)
					rhymeDistance++;
				
				if (i == JA) {
					constraints.get(templateLength).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(rhymeDistance)));
					constraints.get(templateLength-1).add(new ConditionedConstraint<>(new FloatingConstraint<>(rhymeDistance-2, 
							new PartsOfSpeechConstraint<>(new HashSet<Pos>(Arrays.asList(Pos.VBG, Pos.IN, Pos.NN))))));
				} else if (i == MAS) {
					constraints.get(templateLength).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(rhymeDistance)));
				}
				templateLength += 1;
				prevStress = stress;
			}
			
			constraints.get(0).add(new ConditionedConstraint<>(new StartOfWordConstraint<>())); // ensure starts at beginning of a word
			if (prevStress != -1) constraints.get(templateLength-1).add(new ConditionedConstraint<>(new EndOfWordConstraint<>())); // ensure ends at end of a word
			
			markovOrder = rhymeDistance;
			
			if (markovOrder != prevOrder) {
				DataSummary summary = DataLoader.loadData(markovOrder);
				System.out.println("Data loaded for Main.java");
				markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.priors, summary.transitions);
				System.out.println("Creating Markov Model");
			}

			System.out.println("For Rhythmic Template: " + Arrays.toString(rhythmicTemplate));
			// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
			SparseVariableOrderNHMM<SyllableToken> constrainedMarkovModel;
			StopWatch watch = new StopWatch();
			watch.start();
			try {
				System.out.println("Creating " + markovOrder + "-order NHMM of length " + templateLength + " with constraints:");
				for (int i = 0; i < constraints.size(); i++) {
					System.out.println("\tAt position " + i + ":");
					for (ConditionedConstraint<SyllableToken> constraint : constraints.get(i)) {
						System.out.println("\t\t" + constraint);
					}
				}
				constrainedMarkovModel = new SparseVariableOrderNHMM<>(markovModel, templateLength, constraints);
				System.out.println();
			} catch (UnsatisfiableConstraintSetException e) {
				System.out.println("\t" + e.getMessage());
				watch.stop();
				System.out.println("Time to build model:" + watch.getTime());
				continue;
			}
			watch.stop();
			System.out.println("Time to build model:" + watch.getTime());
			
			for (int i = 0; i < 5; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<SyllableToken> generatedSequence = constrainedMarkovModel.generate(templateLength);
				// convert the sequence of syllable tokens to a human-readable string
				System.out.print("\tHave you ever seen ");
				for (SyllableToken syllableToken : generatedSequence) {
					System.out.print(syllableToken.getStringRepresentationIfFirstSyllable() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":""));
				}
				System.out.println("down by the bay?");
				System.out.println("\t\t" + generatedSequence + "\tProb:" + constrainedMarkovModel.probabilityOfSequence(generatedSequence.toArray(new Token[0])));
			}
			
			prevOrder = markovOrder;
		}
	}

	public static void setupRootPath() {
		//Set the root path of Lyrist in U
		final File currentDirFile = new File("");
		Main.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
