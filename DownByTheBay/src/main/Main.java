package main;
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
import linguistic.Phoneme;
import linguistic.Pos;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMM;

public class Main {

	static int markovOrder = 8;
	
	final static int HAVE = 0, YOU = 1, EV = 2, ER = 3, SEEN = 4, A = 5, LLA = 6, MA = 7,
			WEAR = 8, ING = 9, POL = 10, KA = 11, DOT = 12, PA = 13, JA = 14, MAS = 15;
	
	public static void main(String[] args){

		int[] rhythmicSuperTemplate = new int[]{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0};
		
		// a constraint is a {syllable position, feature index, value}
		List<List<Constraint<SyllableToken>>> constraints = new ArrayList<List<Constraint<SyllableToken>>>();
		for (int i = 0; i < rhythmicSuperTemplate.length; i++) {
			final ArrayList<Constraint<SyllableToken>> constraintsForPosition = new ArrayList<Constraint<SyllableToken>>();
			constraints.add(constraintsForPosition);
			constraintsForPosition.add(new StressConstraint<SyllableToken>(rhythmicSuperTemplate[i]));
		}
		
		// Add primer constraints (i.e., "Have you ever seen")
		constraints.get(HAVE).add(new PhonemesConstraint<SyllableToken>(new ArrayList<Phoneme>(Arrays.asList(Phoneme.HH, Phoneme.AE, Phoneme.V))));
		constraints.get(YOU).add(new PhonemesConstraint<SyllableToken>(new ArrayList<Phoneme>(Arrays.asList(Phoneme.Y, Phoneme.UW))));
		constraints.get(EV).add(new PhonemesConstraint<SyllableToken>(new ArrayList<Phoneme>(Arrays.asList(Phoneme.EH, Phoneme.V))));
		constraints.get(ER).add(new PhonemesConstraint<SyllableToken>(new ArrayList<Phoneme>(Arrays.asList(Phoneme.ER))));
		constraints.get(SEEN).add(new PhonemesConstraint<SyllableToken>(new ArrayList<Phoneme>(Arrays.asList(Phoneme.S, Phoneme.IY, Phoneme.N))));
		
		// Add rest of constraints
		constraints.get(A).add(new PartOfSpeechConstraint<SyllableToken>(Pos.DT));
		constraints.get(LLA).add(new PartsOfSpeechConstraint<SyllableToken>(new Pos[]{Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS}));
		constraints.get(JA).add(new PartsOfSpeechConstraint<SyllableToken>(new Pos[]{Pos.NN, Pos.NNS, Pos.NNP, Pos.NNPS, Pos.VBG, Pos.JJ, Pos.RB}));
		constraints.get(JA).add(new BinaryRhymeConstraint<SyllableToken>((JA-LLA)));
		constraints.get(JA).add(new BinaryRhymeConstraint<SyllableToken>((MAS-MA)));
		
		// train a variable-order markov model on a corpus
		DataSummary summary = DataLoader.loadAndAnalyzeData(markovOrder);
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = new SparseVariableOrderMarkovModel<SyllableToken>(summary.statesByIndex, summary.transitions);
		
		// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
		SparseVariableOrderNHMM<SyllableToken> constrainedMarkovModel = new SparseVariableOrderNHMM<SyllableToken>(markovModel, markovOrder, constraints);
			// use double rhymeScore(syl1, syl2)
		
		for (int i = 0; i < 20; i++) {
			// generate a sequence of syllable tokens that meet the constraints
			
			// convert the sequence of syllable tokens to a human-readable string
			
		}
	}
}
