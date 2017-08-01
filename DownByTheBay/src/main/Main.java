package main;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;

import constraint.AbsoluteStressConstraint;
import constraint.BinaryRhymeConstraint;
import constraint.ConditionedConstraint;
import constraint.EndOfWordConstraint;
import constraint.FloatingDBTBPOSSequenceConstraint;
import constraint.PartsOfSpeechConstraint;
import constraint.StartOfWordConstraint;
import constraint.WordsConstraint;
import data.DataLoader;
import data.DataLoader.DataSummary;
import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMMMultiThreaded;
import markov.Token;
import markov.UnsatisfiableConstraintSetException;

public class Main {

	static int markovOrder;
	
//	final static int HAVE = 0, YOU = 1, EV = 2, ER = 3, SEEN = 4, 
		final static int A = 0, LLA = 1, MA = 2,
			WEAR = 3, ING = 4, POL = 5, KA = 6, DOT = 7, PA = 8, JA = 9, MAS = 10;

	public static String rootPath = "";
	
	public static void main(String[] args) throws InterruptedException{

		setupRootPath();
		
		if (args.length > 0) {
			DataLoader.trainingSource = args[0];
		}

		int[] rhythmicSuperTemplate = new int[]{-1,1,-1,1,-1,1,-1,1,-1,1,-1};
		
		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<SyllableToken>>> generalConstraints = new ArrayList<>();
		for (int i = 0; i < rhythmicSuperTemplate.length; i++) {
			final ArrayList<ConditionedConstraint<SyllableToken>> constraintsForPosition = new ArrayList<>();
			generalConstraints.add(constraintsForPosition);
		}
		
		// Add rest of constraints
//		generalConstraints.get(A).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.DT, Pos.JJ)))));
		generalConstraints.get(A).add(new ConditionedConstraint<>(new WordsConstraint<>(new HashSet<>(Arrays.asList("a","an")), false)));
//		generalConstraints.get(A).add(new ConditionedConstraint<>(new HasCodaConstraint<>(), false));
		generalConstraints.get(LLA).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS)))));
		generalConstraints.get(MA).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS)))));
//		generalConstraints.get(JA).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS, Pos.JJ, Pos.VB, Pos.VBD, Pos.VBG, Pos.VBN, Pos.VBP, Pos.VBZ)))));
//		generalConstraints.get(MAS).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS, Pos.JJ, Pos.VB, Pos.VBD, Pos.VBG, Pos.VBN, Pos.VBP, Pos.VBZ)))));
		
		// train a high-order markov model on a corpus
		
		int[][] allRhythmicTemplates = new int[][] {
			new int[]{0,1,-1,-1,-1,1,0,-1,0,1,-1}, // "a bear . . . combing . his hair ."
			new int[]{0,1,-1,-1,-1,1,0,1,0,1,-1}, // "a law . . . drinking from a straw ."
			new int[]{0,1,0,-1,-1,1,0,-1,0,1,0}, // "a llama wearing pajamas"
			new int[]{0,1,-1,1,0,1,0,1,-1,1,-1}, //"a moose . with a pair of new . shoes ."
			new int[]{0,1,0,1,0,1,0,1,0,1,0}, // "a llama wearing polka dot pajamas"
		};
		
		int prevOrder = -1;
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = null;
		for (int[] rhythmicTemplate : allRhythmicTemplates) {
			memoryCheck();
			List<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<>();
			for (List<ConditionedConstraint<SyllableToken>> allConstraintsAtPosition : generalConstraints) {
				constraints.add(new ArrayList<>(allConstraintsAtPosition));
			}
			int templateLength = 0;
			int rhymeDistance = 0;
			// add start of word constraint at beginning
			constraints.get(0).add(new ConditionedConstraint<>(new StartOfWordConstraint<>())); // ensure starts at beginning of a word
			// add end of word constraint after first noun
			constraints.get(rhythmicTemplate[2] == 0?2:1).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
			
			int lastNonNegativeStress = -1;
			boolean nonNNTokenMarked = false;
			for (int i = 0; i < rhythmicTemplate.length; i++) {
				int stress = rhythmicTemplate[i];
				if (stress == -1){
					constraints.remove(templateLength);
					continue;
				} else {
					if (i > 2 && !nonNNTokenMarked) {
						nonNNTokenMarked = true;
						constraints.get(templateLength).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS))), false));
					}
					if (stress == 1) {
//						if (lastNonNegativeStress == 0) { // UNCOMMENT FOR RELATIVE STRESS
//							constraints.get(templateLength).add(new ConditionedConstraint<>(new RelativeStressConstraint<>(stress, 1)));
//						} else {
							constraints.get(templateLength).add(new ConditionedConstraint<>(new AbsoluteStressConstraint<>(stress)));
//						}
					}
					
					lastNonNegativeStress = stress;
				}
				
				if (i > LLA && i <= JA)
					rhymeDistance++;
				
				if (i == JA) {
					constraints.get(templateLength).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(rhymeDistance)));
					constraints.get(templateLength).add(new ConditionedConstraint<>(new FloatingDBTBPOSSequenceConstraint<>()));
				} else if (i == MAS) {
					constraints.get(templateLength).add(new ConditionedConstraint<>(new BinaryRhymeConstraint<>(rhymeDistance)));
				}
				templateLength += 1;
			}
			
			// add end of word constraint at end
			constraints.get(templateLength-1).add(0, new ConditionedConstraint<>(new EndOfWordConstraint<>())); // ensure ends at end of a word
			
			markovOrder = rhymeDistance;
			
			StopWatch watch = new StopWatch();
			if (markovOrder != prevOrder) {
				markovModel = null; // allow this to get cleaned by the garbage collector before building the next model
				memoryCheck();
				watch.start();
				DataLoader dl = new DataLoader(markovOrder);
				DataSummary summary = dl.loadData();
				System.out.println("Data loaded for Main.java");
				watch.stop();
				System.out.println("Time to train on data:" + watch.getTime());
				watch.reset();
				memoryCheck();
				markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.priors, summary.transitions);
				System.out.println("Creating Markov Model");
			}

			System.out.println("For Rhythmic Template: " + Arrays.toString(rhythmicTemplate));
			// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
			SparseVariableOrderNHMMMultiThreaded<SyllableToken> constrainedMarkovModel;
			watch.start();
			try {
				System.out.println("Creating " + markovOrder + "-order NHMM of length " + templateLength + " with constraints:");
				for (int i = 0; i < constraints.size(); i++) {
					System.out.println("\tAt position " + i + ":");
					for (ConditionedConstraint<SyllableToken> constraint : constraints.get(i)) {
						System.out.println("\t\t" + constraint);
					}
				}
				constrainedMarkovModel = new SparseVariableOrderNHMMMultiThreaded<>(markovModel, templateLength, constraints);
				memoryCheck();

				System.out.println();
			} catch (UnsatisfiableConstraintSetException e) {
				System.out.println("\t" + e.getMessage());
				watch.stop();
				System.out.println("Time to build model:" + watch.getTime());
				continue;
			}
			watch.stop();
			System.out.println("Time to build model:" + watch.getTime());
			
			System.out.println("Finished creating " + markovOrder + "-order NHMM of length " + templateLength + " with constraints:");
			for (int i = 0; i < constraints.size(); i++) {
				System.out.println("\tAt position " + i + ":");
				for (ConditionedConstraint<SyllableToken> constraint : constraints.get(i)) {
					System.out.println("\t\t" + constraint);
				}
			}
			
//			for (int i = 0; i < 20; i++) {
				// generate a sequence of syllable tokens that meet the constraints
//				List<SyllableToken> generatedSequence = constrainedMarkovModel.generate(templateLength);
			for(List<SyllableToken> generatedSequence : constrainedMarkovModel.generateFromAllPriors(templateLength)) {
				// convert the sequence of syllable tokens to a human-readable string
				System.out.print("\tHave you ever seen ");
				for (SyllableToken syllableToken : generatedSequence) {
					System.out.print(syllableToken.getStringRepresentationIfFirstSyllable() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":""));
				}
//				System.out.println("down by the bay?");
				System.out.println();
				System.out.print("\t\t");
				boolean first = true;
				for (SyllableToken syllableToken : generatedSequence) {
					if (first) {
						first = false;
					} else {
						System.out.print(",");
					}
					System.out.print("Pos." + syllableToken.getPos());
				}
				System.out.println();
				System.out.println("\t\t" + generatedSequence + "\tProb:" + constrainedMarkovModel.probabilityOfSequence(generatedSequence.toArray(new Token[0])));
			}
			
			prevOrder = markovOrder;
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
		Main.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
