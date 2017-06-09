package main;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import constraint.BinaryRhymeConstraint;
import constraint.Constraint;
import constraint.EndOfWordConstraint;
import constraint.PartOfSpeechConstraint;
import constraint.PartOfSpeechInSegmentConstraint;
import constraint.PartsOfSpeechConstraint;
import constraint.StartOfWordConstraint;
import constraint.StressConstraint;
import data.DataLoader;
import data.DataLoader.DataSummary;
import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMM;
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
		List<List<Constraint<SyllableToken>>> allConstraints = new ArrayList<>();
		for (int i = 0; i < rhythmicSuperTemplate.length; i++) {
			final ArrayList<Constraint<SyllableToken>> constraintsForPosition = new ArrayList<>();
			allConstraints.add(constraintsForPosition);
			constraintsForPosition.add(new StressConstraint<>(rhythmicSuperTemplate[i]));
		}
		
		// Add rest of constraints
		allConstraints.get(A).add(new PartOfSpeechConstraint<>(Pos.DT));
		allConstraints.get(LLA).add(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS))));
		allConstraints.get(JA).add(new PartsOfSpeechConstraint<>(new HashSet<>(Arrays.asList(Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS, Pos.VBG, Pos.JJ, Pos.RB))));
		
		// train a high-order markov model on a corpus
		
		int[][] allRhythmicTemplates = new int[][] {
			new int[]{0,1,-1,-1,-1,1,0,-1,0,1,-1}, // "a bear . . . combing . his hair ."
			new int[]{0,1,0,1,0,1,0,1,0,1,0}, // "a llama wearing polka dot pajamas"
			new int[]{0,1,0,-1,-1,1,0,-1,0,1,0}, // "a llama wearing pajamas"
			new int[]{0,1,-1,1,0,1,0,1,-1,1,-1} //"a moose . with a pair of new . shoes ."
		};
		
		for (int[] rhythmicTemplate : allRhythmicTemplates) {
			List<List<Constraint<SyllableToken>>> constraints = new ArrayList<>();
			for (List<Constraint<SyllableToken>> allConstraintsAtPosition : allConstraints) {
				constraints.add(new ArrayList<>(allConstraintsAtPosition));
			}
			int templateLength = 0;
			int rhymeDistance = 0;
			
			for (int i = 0; i < rhythmicTemplate.length; i++) {
				int stress = rhythmicTemplate[i];
				if (stress == -1){
					constraints.remove(templateLength);
					continue;
				}
				
				if (i > LLA && i <= JA)
					rhymeDistance++;
				
				if (i == JA) {
					constraints.get(templateLength).add(new BinaryRhymeConstraint<>(rhymeDistance));
					constraints.get(templateLength-1).add(new PartOfSpeechInSegmentConstraint<SyllableToken>(rhymeDistance-2, new HashSet<>(Arrays.asList(Pos.VBG, Pos.IN, Pos.NN))));
				} else if (i == MAS) {
					constraints.get(templateLength).add(new BinaryRhymeConstraint<>(rhymeDistance));
				}
				templateLength += 1;
			}
			
			allConstraints.get(0).add(new StartOfWordConstraint<>());
			allConstraints.get(templateLength-1).add(new EndOfWordConstraint<>());
			
			markovOrder = rhymeDistance;
			DataSummary summary = DataLoader.loadData(markovOrder); // TODO: replace with actual data loader
			
			SparseVariableOrderMarkovModel<SyllableToken> markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.transitions);
			
			System.out.println("For Rhythmic Template: " + Arrays.toString(rhythmicTemplate));
			// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
			SparseVariableOrderNHMM<SyllableToken> constrainedMarkovModel;
			try {
				System.out.print("Creating NHMM");
				constrainedMarkovModel = new SparseVariableOrderNHMM<>(markovModel, templateLength, constraints);
				System.out.println();
			} catch (UnsatisfiableConstraintSetException e) {
				System.out.println("\t" + e.getMessage());
				continue;
			}
			
			for (int i = 0; i < 5; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<SyllableToken> generatedSequence = constrainedMarkovModel.generateWithAnyPrefix(templateLength);
				// convert the sequence of syllable tokens to a human-readable string
				System.out.print("\tHave you ever seen: ");
				for (SyllableToken syllableToken : generatedSequence) {
					System.out.print(syllableToken.getStringRepresentationIfFirstSyllable() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":"")); // TODO: modify it to print out the human-readable form of the syllable/word
				}
				System.out.println();
			}
		}
	}

	public static void setupRootPath() {
		//Set the root path of Lyrist in U
		final File currentDirFile = new File("");
		Main.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
