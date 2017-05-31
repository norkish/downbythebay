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
import data.DataLoader.DataSummary;
import data.SyllableToken;
import linguistic.phonetic.PhonemeEnum;
import linguistic.syntactic.Pos;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMM;

public class Main {

	static int markovOrder = 8;
	
	final static int HAVE = 0, YOU = 1, EV = 2, ER = 3, SEEN = 4, A = 5, LLA = 6, MA = 7,
			WEAR = 8, ING = 9, POL = 10, KA = 11, DOT = 12, PA = 13, JA = 14, MAS = 15;

	public static String rootPath = "";
	
	public static void main(String[] args){

		setupRootPath();

		int[] rhythmicSuperTemplate = new int[]{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0};
		
		// a constraint is a {syllable position, feature index, value}
		List<List<Constraint<SyllableToken>>> constraints = new ArrayList<List<Constraint<SyllableToken>>>();
		for (int i = 0; i < rhythmicSuperTemplate.length; i++) {
			final ArrayList<Constraint<SyllableToken>> constraintsForPosition = new ArrayList<Constraint<SyllableToken>>();
			constraints.add(constraintsForPosition);
			constraintsForPosition.add(new StressConstraint<SyllableToken>(rhythmicSuperTemplate[i]));
		}
		
		// Add primer constraints (i.e., "Have you ever seen")
		constraints.get(HAVE).add(new PhonemesConstraint<SyllableToken>(new ArrayList<>(Arrays.asList(PhonemeEnum.HH, PhonemeEnum.AE, PhonemeEnum.V))));
		constraints.get(YOU).add(new PhonemesConstraint<SyllableToken>(new ArrayList<>(Arrays.asList(PhonemeEnum.Y, PhonemeEnum.UW))));
		constraints.get(EV).add(new PhonemesConstraint<SyllableToken>(new ArrayList<>(Arrays.asList(PhonemeEnum.EH, PhonemeEnum.V))));
		constraints.get(ER).add(new PhonemesConstraint<SyllableToken>(new ArrayList<>(Arrays.asList(PhonemeEnum.ER))));
		constraints.get(SEEN).add(new PhonemesConstraint<SyllableToken>(new ArrayList<>(Arrays.asList(PhonemeEnum.S, PhonemeEnum.IY, PhonemeEnum.N))));
		
		// Add rest of constraints
		constraints.get(A).add(new PartOfSpeechConstraint<>(Pos.DT));
		constraints.get(LLA).add(new PartsOfSpeechConstraint<>(new Pos[]{Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS}));
		constraints.get(JA).add(new PartsOfSpeechConstraint<>(new Pos[]{Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS, Pos.VBG, Pos.JJ, Pos.RB}));
		constraints.get(JA).add(new BinaryRhymeConstraint<>((JA-LLA)));
		constraints.get(JA).add(new BinaryRhymeConstraint<>((MAS-MA)));
		
		// train a variable-order markov model on a corpus
		DataSummary summary = DataLoader.loadAndAnalyzeData(markovOrder, "corpus.txt");
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.transitions);
		
		// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
		SparseVariableOrderNHMM<SyllableToken> constrainedMarkovModel = new SparseVariableOrderNHMM<>(markovModel, markovOrder, constraints);
			// use double rhymeScore(syl1, syl2)
		
		for (int i = 0; i < 20; i++) {
			// generate a sequence of syllable tokens that meet the constraints
			
			// convert the sequence of syllable tokens to a human-readable string
			
		}
	}

	public static void setupRootPath() {
		//Set the root path of Lyrist in U
		final File currentDirFile = new File("");
		Main.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
