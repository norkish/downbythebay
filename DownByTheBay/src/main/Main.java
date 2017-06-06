package main;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import constraint.BinaryRhymeConstraint;
import constraint.Constraint;
import constraint.PartOfSpeechConstraint;
import constraint.PartsOfSpeechConstraint;
import constraint.PhonemesConstraint;
import constraint.StressConstraint;
import data.DataLoader;
import data.DummyDataLoader;
import data.DataLoader.DataSummary;
import data.SyllableToken;
import linguistic.phonetic.PhonemeEnum;
import linguistic.syntactic.Pos;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMM;
import markov.UnsatisfiableConstraintSetException;

public class Main {

	static int markovOrder = 8;
	
	final static int HAVE = 0, YOU = 1, EV = 2, ER = 3, SEEN = 4, A = 5, LLA = 6, MA = 7,
			WEAR = 8, ING = 9, POL = 10, KA = 11, DOT = 12, PA = 13, JA = 14, MAS = 15;

	public static String rootPath = "";
	
	public static void main(String[] args){

		setupRootPath();

		int[] rhythmicSuperTemplate = new int[]{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0};
		
		// a constraint is a {syllable position, feature index, value}
		List<List<Constraint<SyllableToken>>> allConstraints = new ArrayList<List<Constraint<SyllableToken>>>();
		for (int i = 0; i < rhythmicSuperTemplate.length; i++) {
			final ArrayList<Constraint<SyllableToken>> constraintsForPosition = new ArrayList<Constraint<SyllableToken>>();
			allConstraints.add(constraintsForPosition);
			constraintsForPosition.add(new StressConstraint<SyllableToken>(rhythmicSuperTemplate[i]));
		}
		
		// Add primer constraints (i.e., "Have you ever seen")
		allConstraints.get(HAVE).add(new PhonemesConstraint<SyllableToken>(new ArrayList<PhonemeEnum>(Arrays.asList(PhonemeEnum.HH, PhonemeEnum.AE, PhonemeEnum.V))));
		allConstraints.get(YOU).add(new PhonemesConstraint<SyllableToken>(new ArrayList<PhonemeEnum>(Arrays.asList(PhonemeEnum.Y, PhonemeEnum.UW))));
		allConstraints.get(EV).add(new PhonemesConstraint<SyllableToken>(new ArrayList<PhonemeEnum>(Arrays.asList(PhonemeEnum.EH))));
		allConstraints.get(ER).add(new PhonemesConstraint<SyllableToken>(new ArrayList<PhonemeEnum>(Arrays.asList(PhonemeEnum.V, PhonemeEnum.ER))));
		allConstraints.get(SEEN).add(new PhonemesConstraint<SyllableToken>(new ArrayList<PhonemeEnum>(Arrays.asList(PhonemeEnum.S, PhonemeEnum.IY, PhonemeEnum.N))));
		
		// Add rest of constraints
//		constraints.get(A).add(new PartOfSpeechConstraint<SyllableToken>(Pos.DT));
		allConstraints.get(LLA).add(new PartsOfSpeechConstraint<SyllableToken>(new Pos[]{Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS}));
		allConstraints.get(JA).add(new PartsOfSpeechConstraint<SyllableToken>(new Pos[]{Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS, Pos.VBG, Pos.JJ, Pos.RB}));
		
		// train a variable-order markov model on a corpus
//		DataSummary summary = DataLoader.loadAndAnalyzeData(markovOrder, "corpus.txt");
		DataSummary summary = DummyDataLoader.loadData(); // TODO: replace with actual data loader
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = new SparseVariableOrderMarkovModel<SyllableToken>(summary.statesByIndex, summary.transitions);
		
		int[][] allRhythmicTemplates = new int[][] {
			new int[]{1,0,1,0,1,0,1,-1,-1,-1,1,0,-1,0,1,-1}, // "a bear . . . combing . his hair ."
			new int[]{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0}, // "a llama wearing polka dot pajamas"
			new int[]{1,0,1,0,1,0,1,0,-1,-1,1,0,-1,0,1,0}, // "a llama wearing pajamas"
			new int[]{1,0,1,0,1,0,1,-1,1,0,1,0,1,-1,1,-1} //"a moose . with a pair of new . shoes ."
		};
		
		for (int[] rhythmicTemplate : allRhythmicTemplates) {
			List<List<Constraint<SyllableToken>>> constraints = new ArrayList<List<Constraint<SyllableToken>>>();
			for (List<Constraint<SyllableToken>> allConstraintsAtPosition : allConstraints) {
				constraints.add(new ArrayList<Constraint<SyllableToken>>(allConstraintsAtPosition));
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
				
				if (i == JA)
					constraints.get(templateLength).add(new BinaryRhymeConstraint<SyllableToken>(rhymeDistance));
				else if (i == MAS)
					constraints.get(templateLength).add(new BinaryRhymeConstraint<SyllableToken>(rhymeDistance));
				templateLength += 1;
			}
			
			System.out.println("For Rhythmic Tempalate: " + Arrays.toString(rhythmicTemplate));
			// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
			SparseVariableOrderNHMM<SyllableToken> constrainedMarkovModel;
			try {
				constrainedMarkovModel = new SparseVariableOrderNHMM<SyllableToken>(markovModel, templateLength, constraints);
			} catch (UnsatisfiableConstraintSetException e) {
				System.out.println("\t" + e.getMessage());
				continue;
			}
			
			for (int i = 0; i < 2; i++) {
				// generate a sequence of syllable tokens that meet the constraints
				List<SyllableToken> generatedSequence = constrainedMarkovModel.generate(templateLength);
				// convert the sequence of syllable tokens to a human-readable string
				System.out.print("\t");
				for (SyllableToken syllableToken : generatedSequence) {
					System.out.print(syllableToken.getStringRepresentation() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":"")); // TODO: modify it to print out the human-readable form of the syllable/word
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
