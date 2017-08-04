package main;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import constraint.ConditionedConstraint;
import constraint.MatchStressToBeatsConstraint;
import constraint.TimeSignatureConstraint;
import data.DataLoader.DataSummary;
import data.MusicDataLoader;
import data.RhythmToken;
import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.syllabic.Syllable;
import linguistic.phonetic.syllabic.WordSyllables;
import markov.SparseVariableOrderMarkovModel;
import markov.SparseVariableOrderNHMMMultiThreaded;
import markov.UnsatisfiableConstraintSetException;
import semantic.word2vec.BadW2vInputException;

public class RhythmsMain {

	static int markovOrder = 4;
	
	public static String rootPath = "";
	
	public static void main(String[] args) throws InterruptedException, BadW2vInputException, FileNotFoundException{

		setupRootPath();

		String sentenceToProsodize = "No more monkeys jumping on the bed";
		
		System.out.println("Finding stress patterns for \"" + sentenceToProsodize + "\":");
		List<Integer> stresses = getStressPattern(sentenceToProsodize);
		System.out.print("\t[");
		boolean first = true;
		for (Integer stress : stresses) {
			if (first) first = false;
			else System.out.print(",");
			System.out.print(stress);
		}
		System.out.println("]");

		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<RhythmToken>>> constraints = new ArrayList<>();
		for (int i = 0; i < stresses.size(); i++) {
			final ArrayList<ConditionedConstraint<RhythmToken>> constraintsForPosition = new ArrayList<>();
			if (i >= markovOrder) constraintsForPosition.add(new ConditionedConstraint<RhythmToken>(new TimeSignatureConstraint(4,4)));
			constraints.add(constraintsForPosition);
		}
		
		int prevStressConstraintPosition = -1;
		for (int stressConstraintPosition = Math.min(markovOrder,stresses.size()); prevStressConstraintPosition != stressConstraintPosition; stressConstraintPosition = Math.min(stressConstraintPosition+markovOrder+1,stresses.size()-1)) {
			System.out.println("Floating constraint covers positions from " + (prevStressConstraintPosition+1) + " to " + stressConstraintPosition + " inclusive");
			constraints.get(stressConstraintPosition).add(new ConditionedConstraint<>(new MatchStressToBeatsConstraint(stresses.subList(prevStressConstraintPosition+1, stressConstraintPosition+1), .8)));
			
			prevStressConstraintPosition = stressConstraintPosition;
		}
		
		// it must start on a downbeat (possibly a rest)
//		constraints.get(0).add(new ConditionedConstraint<>(new DownBeatStartConstraint()));
		// it must end on a downbeat (not a rest)
//		constraints.get(COMPOSITION_LEN-1).add(new ConditionedConstraint<>(new DownBeatStartConstraint()));
//		constraints.get(COMPOSITION_LEN-1).add(new ConditionedConstraint<>(new RestContraint(),false));
		
		MusicDataLoader mdl = new MusicDataLoader(markovOrder);
		DataSummary<RhythmToken> summary = mdl.loadData();
		System.out.println("Data loaded for HaikuMain.java");
		SparseVariableOrderMarkovModel<RhythmToken> markovModel = new SparseVariableOrderMarkovModel<RhythmToken>(summary.statesByIndex, summary.priors, summary.transitions);
		System.out.println("Creating Markov Model");

		try {
			System.out.println("Creating a " + markovOrder + "-order NHMM of length " + stresses.size() + " with constraints:");
			for (int i = 0; i < constraints.size(); i++) {
				System.out.println("\tAt position " + i + ":");
				for (ConditionedConstraint<RhythmToken> constraint : constraints.get(i)) {
					System.out.println("\t\t" + constraint);
				}
			}
			SparseVariableOrderNHMMMultiThreaded<RhythmToken> constrainedMarkovModel = new SparseVariableOrderNHMMMultiThreaded<>(markovModel, stresses.size(), constraints);
			System.out.println();

			Set<String> compositions = new HashSet<String>();
			
			for (List<RhythmToken> composition : constrainedMarkovModel.generateFromAllPriors(stresses.size())) {
				StringBuilder str = new StringBuilder();
				for (int j = 0; j < composition.size(); j++) {
					if (j!=0) str.append(" ");
					RhythmToken rhythmToken = composition.get(j);
					str.append(rhythmToken);
				}
				compositions.add(str.toString());
			}
			for (String composition : compositions) {
				System.out.println(sentenceToProsodize);
				System.out.println(composition);
				System.out.println();
			}
			
		} catch (UnsatisfiableConstraintSetException e) {
			System.out.println("\t" + e.getMessage());
		}
	}

	private static List<Integer> getStressPattern(String sentenceToProsodize) {
		List<Integer> stresses = new ArrayList<Integer>();

		String[] split = sentenceToProsodize.split(" ");
		for (String word : split) {
			List<WordSyllables> pronunciations = Phoneticizer.syllableDict.get(word.toUpperCase());
			if (pronunciations == null) {
				pronunciations = Phoneticizer.useG2P(word.toUpperCase());
			}
			if (pronunciations == null || pronunciations.isEmpty()) return null;
			
			WordSyllables pronunciation = pronunciations.get(0);
			for (Syllable syllable : pronunciation) {
				stresses.add(syllable.getStress() == 1?1:0);
			}
		}
		
		return stresses;
	}

	public static void setupRootPath() {
		//Set the root path of Lyrist in U
		final File currentDirFile = new File("");
		RhythmsMain.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
