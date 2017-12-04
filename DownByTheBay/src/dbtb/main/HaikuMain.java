package dbtb.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import dbtb.constraint.ConditionedConstraint;
import dbtb.constraint.EndOfWordConstraint;
import dbtb.constraint.FloatingConstraint;
import dbtb.constraint.FloatingHaikuPOSSequenceConstraint;
import dbtb.constraint.PartsOfSpeechConstraint;
import dbtb.constraint.SemanticMeaningConstraint;
import dbtb.constraint.StartOfWordConstraint;
import dbtb.data.DataLoader;
import dbtb.data.SyllableToken;
import dbtb.data.DataLoader.DataSummary;
import dbtb.linguistic.syntactic.Pos;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderNHMMMultiThreaded;
import dbtb.markov.UnsatisfiableConstraintSetException;
import dbtb.semantic.word2vec.BadW2vInputException;

public class HaikuMain {

	static int markovOrder = 4;
	
	public static String rootPath = "";
	
	private static final int LINE1_LEN = 5; 
	private static final int LINE2_LEN = 7;
	private static final int LINE3_LEN = 5;
	private static final int HAIKU_LEN = LINE1_LEN + LINE2_LEN + LINE3_LEN;
	
	public static void main(String[] args) throws InterruptedException, BadW2vInputException, FileNotFoundException{

		setupRootPath();

		// a constraint is a {syllable position, feature index, value}
		List<List<ConditionedConstraint<SyllableToken>>> constraints = new ArrayList<>();
		for (int i = 0; i < HAIKU_LEN; i++) {
			final ArrayList<ConditionedConstraint<SyllableToken>> constraintsForPosition = new ArrayList<>();
			constraints.add(constraintsForPosition);
		}
		
		final HashSet<Pos> disallowedPosAtPhraseEnd = new HashSet<Pos>(Arrays.asList(Pos.DT, Pos.IN, Pos.CC, Pos.TO, Pos.PRP$, Pos.WP$, Pos.WRB));
		
//		loadHaikuWord();
		final HashSet<String> themeWords = new HashSet<String>(Arrays.asList(
				"nature",
				"animals",
				"trees",
				"tree",
				"forest",
				"undergrowth",
				"green",
				"shadows",
				"shadow",
				"wind",
				"breeze",
				"animal",
				"sun",
				"sunset",
				"air",
				"light",
				"beach",
				"sand",
				"ocean",
				"beauty",
				"breeze",
				"grass",
				"mount",
				"mountain",
				"mountains",
				"rock",
				"viewpoint",
				"climbing",
				"clouds",
				"sunrise",
				"sunset",
				"horizon",
				"sky",
				"peak",
				"ascend",
				"wind",
				"water",
				"stream",
				"river",
				"trickle",
				"splash",
				"running",
				"destination",
				"journey",
				"home",
				"voyage",
				"earth",
				"fire"
				));
		
		// train a high-order markov model on a corpus
		constraints.get(0).add(new ConditionedConstraint<>(new StartOfWordConstraint<>()));
		
		constraints.get(4).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(4).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(disallowedPosAtPhraseEnd), false));
		constraints.get(4).add(new ConditionedConstraint<>(new FloatingConstraint<SyllableToken>(markovOrder, new SemanticMeaningConstraint<SyllableToken>(
//				new HashSet<String>(Arrays.asList("love"))
				themeWords
				))));
		constraints.get(4).add(new ConditionedConstraint<>(new FloatingHaikuPOSSequenceConstraint<>(1)));
		
		constraints.get(5).add(new ConditionedConstraint<>(new StartOfWordConstraint<>()));
//		constraints.get(5).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(disallowedPosAtPhraseEnd), false));
		
		constraints.get(11).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(11).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(disallowedPosAtPhraseEnd), false));
		constraints.get(11).add(new ConditionedConstraint<>(new FloatingConstraint<SyllableToken>(markovOrder, new SemanticMeaningConstraint<SyllableToken>(
//				new HashSet<String>(Arrays.asList("disappointment"))
				themeWords
				))));
		constraints.get(11).add(new ConditionedConstraint<>(new FloatingHaikuPOSSequenceConstraint<>(2)));
		
		constraints.get(12).add(new ConditionedConstraint<>(new StartOfWordConstraint<>()));
//		constraints.get(12).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(disallowedPosAtPhraseEnd), false));
		
//		constraints.get(16).add(new ConditionedConstraint<>(new PartsOfSpeechConstraint<>(disallowedPosAtPhraseEnd), false));
		constraints.get(16).add(new ConditionedConstraint<>(new EndOfWordConstraint<>()));
		constraints.get(16).add(new ConditionedConstraint<>(new FloatingConstraint<SyllableToken>(markovOrder, new SemanticMeaningConstraint<SyllableToken>(
//				new HashSet<String>(Arrays.asList("earth","beauty"))
				themeWords
				))));
		constraints.get(16).add(new ConditionedConstraint<>(new FloatingHaikuPOSSequenceConstraint<>(3)));
		
//		for (int i = 0; i < 17; i++) {
//			constraints.get(i).add(new ConditionedConstraint<>(new WordsConstraint<>(haikuWords, false)));
//		}
		
		DataLoader dl = new DataLoader(markovOrder);
		DataSummary summary = dl.loadData();
		System.out.println("Data loaded for HaikuMain.java");
		SparseVariableOrderMarkovModel<SyllableToken> markovModel = new SparseVariableOrderMarkovModel<>(summary.statesByIndex, summary.priors, summary.transitions);
		System.out.println("Creating Markov Model");

		// create a constrained markov model of length rhythmicSuperTemplate.length and with constraints in constraints
		try {
//			System.out.println("Creating 3 " + markovOrder + "-order NHMM of lengths 5, 7, 5 with constraints:");
			System.out.println("Creating a " + markovOrder + "-order NHMM of length 17 with constraints:");
			for (int i = 0; i < constraints.size(); i++) {
				System.out.println("\tAt position " + i + ":");
				for (ConditionedConstraint<SyllableToken> constraint : constraints.get(i)) {
					System.out.println("\t\t" + constraint);
				}
			}
			SparseVariableOrderNHMMMultiThreaded<SyllableToken> constrainedMarkovModel = new SparseVariableOrderNHMMMultiThreaded<>(markovModel, HAIKU_LEN, constraints);
//			SparseVariableOrderNHMMMultiThreaded<SyllableToken> constrainedMarkovModel5 = new SparseVariableOrderNHMMMultiThreaded<>(markovModel, 5, constraints.subList(0, 5));
//			SparseVariableOrderNHMMMultiThreaded<SyllableToken> constrainedMarkovModel7 = new SparseVariableOrderNHMMMultiThreaded<>(markovModel, 7, constraints.subList(5, 12));
//			SparseVariableOrderNHMMMultiThreaded<SyllableToken> constrainedMarkovModel52 = new SparseVariableOrderNHMMMultiThreaded<>(markovModel, 5, constraints.subList(12, constraints.size()));
			System.out.println();

			Set<String> haikus = new HashSet<String>();
			
			for (List<SyllableToken> haikuLine : constrainedMarkovModel.generateFromAllPriors(HAIKU_LEN)) {
				StringBuilder str = new StringBuilder();
				for (int j = 0; j < haikuLine.size(); j++) {
					if (j == 5 || j == 12) 
						str.append('\n');
					SyllableToken syllableToken = haikuLine.get(j);
					str.append(syllableToken.getStringRepresentationIfFirstSyllable() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":""));
				}
				haikus.add(str.toString());
			}
			for (String haiku : haikus) {
				System.out.println(haiku);
				System.out.println();
			}
			
			// generate a sequence of syllable tokens that meet the constraints
//			for (int i = 0; i < 100; i++) {
//				List<List<SyllableToken>> haiku = new ArrayList<List<SyllableToken>>();
//				haiku.add(constrainedMarkovModel5.generate(5));
//				haiku.add(constrainedMarkovModel7.generate(7));
//				haiku.add(constrainedMarkovModel52.generate(5));
//				// convert the sequence of syllable tokens to a human-readable string
//				for (List<SyllableToken> haikuLine : haiku) {
//					for (int j = 0; j < haikuLine.size(); j++) {
//						SyllableToken syllableToken = haikuLine.get(j);
//						System.out.print(syllableToken.getStringRepresentationIfFirstSyllable() + (syllableToken.getPositionInContext() == syllableToken.getCountOfSylsInContext()-1?" ":""));
//					}
//					System.out.println();
//				}
//				System.out.println();
//			}
		} catch (UnsatisfiableConstraintSetException e) {
			System.out.println("\t" + e.getMessage());
		}
	}

	private final static String HAIKU_FILE = "data/haikus.txt";
	
	public static Map<List<Pos>, Integer> loadHaikuWord(int lineNumber) throws FileNotFoundException {
		Scanner scan = new Scanner(new File(HAIKU_FILE));
		DataLoader dl = new DataLoader(markovOrder);
		
		String line;
		StringBuilder haiku = new StringBuilder();
		
		Map<List<Pos>, Integer> forms = new HashMap<List<Pos>, Integer>();
		List<Pos> form = null;
		
		int numLinesInHaiku = 0;
		while(scan.hasNextLine()) {
			line = scan.nextLine();
			if (line.trim().isEmpty()) {
				if (numLinesInHaiku == 3) {
					final String[] split = haiku.toString().trim().split("\n");
					for (int j = 0; j < split.length; j++) {
						if (j !=4 && j!=(lineNumber-1)) continue;
						String haikuLine = (lineNumber == 4 ? haiku.toString() : split[j]);
						Pos prevPos = null;
						List<List<SyllableToken>> convertToSyllableTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(haikuLine));
						if (convertToSyllableTokens != null && !convertToSyllableTokens.isEmpty()) {
							List<SyllableToken> list = convertToSyllableTokens.get(0);
							list = list.subList(Math.max(1, list.size()-markovOrder), list.size());
//							System.out.println(haikuLine);
							boolean first = true;
							for (int i = 0; i < list.size(); i++) {
								final Pos pos = list.get(i).getPos();
								if (pos != prevPos) {
									if (first) {
//										System.out.print("				new Pos[]{");
										form = new ArrayList<Pos>();
										first = false;
									} else {
//										System.out.print(",");
									}
									form.add(pos);
//									System.out.print("Pos." + pos);
									prevPos = pos;
								}
							}
							if (!first) {
//								System.out.println("},");
								Integer count = forms.putIfAbsent(form, 1);
								if (count != null) {
									forms.put(form, count+1);
								}
							}
						}
					}
				}
				haiku = new StringBuilder();
				numLinesInHaiku = 0;
			} else {
				numLinesInHaiku++;
				haiku.append(line);
				haiku.append("\n");
			}
		}
		
		scan.close();
		return forms;
	}

	public static void setupRootPath() {
		//Set the root path of Lyrist in U
		final File currentDirFile = new File("");
		HaikuMain.rootPath = currentDirFile.getAbsolutePath() + "/";
	}

}
