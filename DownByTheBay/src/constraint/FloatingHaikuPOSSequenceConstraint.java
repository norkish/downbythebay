package constraint;

import java.io.FileNotFoundException;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import data.SyllableToken;
import linguistic.syntactic.Pos;
import main.HaikuMain;
import markov.Token;

public class FloatingHaikuPOSSequenceConstraint<T> implements TransitionalConstraint<T> {

	private int line;
	// if this value, for example, is 2, then the constraint indicates that the syllable 
	// under this constraint must rhyme with the syllable 2 positions before it
	// this must be greater than the Markov order and cannot point to a position before the 0th position 

	public FloatingHaikuPOSSequenceConstraint(int line) {
		this.line = line;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, LinkedList<Token> toState) {
		SyllableToken lastToken = (SyllableToken) toState.getLast();
		
		HaikuGrammarValidator validator = new HaikuGrammarValidator(line);

		for (Token token : fromState) {
			final SyllableToken syllableToken = (SyllableToken) token;
			if(!validator.validate(syllableToken.getPos()))
				return false;
		}
		
		if (!validator.validate(lastToken.getPos()))
			return false;
		
		return validator.isComplete();
	}

	public static class HaikuGrammarNode {

		public Pos pos = null;
		private Map<Pos, HaikuGrammarNode> nextNodesByPos = new EnumMap<Pos,HaikuGrammarNode>(Pos.class);
		private boolean isCompletePath = false;

		public HaikuGrammarNode(Pos pos2) {
			this.pos = pos2;
		}

		public HaikuGrammarNode getNextNodeTo(Pos pos2) {
			return nextNodesByPos.get(pos2);
		}

		public void addPath(Pos[] posPath, int idx) {
			if (idx == posPath.length) {
				this.isCompletePath = true;
				return;
			}
			Pos nextPos = posPath[idx];
			HaikuGrammarNode nextNode = nextNodesByPos.get(nextPos);
			if (nextNode == null) {
				nextNode = new HaikuGrammarNode(nextPos);
				nextNodesByPos.put(nextPos, nextNode);
			}
			
			nextNode.addPath(posPath, idx+1);
		}

		public boolean pathIsComplete() {
			return isCompletePath;
		}

	}
	
	public static class HaikuGrammarValidator {
		
		// took top 20 forms for each line from a haiku dataset
		final private static Pos[][][] trainingPosSet = new Pos[][][]{ 
			new Pos[][] {
				new Pos[]{Pos.VBN,Pos.NN},
				new Pos[]{Pos.VBN},
				new Pos[]{Pos.RB},
				new Pos[]{Pos.IN,Pos.NNS},
				new Pos[]{Pos.NN,Pos.VBZ},
				new Pos[]{Pos.NNP},
				new Pos[]{Pos.NN,Pos.IN,Pos.NN},
				new Pos[]{Pos.VBG,Pos.DT,Pos.NN},
				new Pos[]{Pos.DT,Pos.NN},
				new Pos[]{Pos.JJ},
				new Pos[]{Pos.VBG,Pos.NN},
				new Pos[]{Pos.VBG,Pos.NNS},
				new Pos[]{Pos.IN,Pos.NN},
				new Pos[]{Pos.JJ,Pos.NNS},
				new Pos[]{Pos.VBG},
				new Pos[]{Pos.IN,Pos.DT,Pos.NN},
				new Pos[]{Pos.NN,Pos.NNS},
				new Pos[]{Pos.JJ,Pos.NN},
				new Pos[]{Pos.NNS},
				new Pos[]{Pos.NN},
			},
			new Pos[][] {
				new Pos[]{Pos.NNS,Pos.IN,Pos.DT,Pos.NN},
				new Pos[]{Pos.VBG,Pos.NNS},
				new Pos[]{Pos.IN,Pos.JJ,Pos.NNS},
				new Pos[]{Pos.IN,Pos.NN,Pos.NNS},
				new Pos[]{Pos.NNS},
				new Pos[]{Pos.VBG,Pos.NN},
				new Pos[]{Pos.IN,Pos.NNS},
				new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN},
				new Pos[]{Pos.NN,Pos.IN,Pos.NNS},
				new Pos[]{Pos.IN,Pos.NN},
				new Pos[]{Pos.DT,Pos.JJ,Pos.NN},
				new Pos[]{Pos.VBG,Pos.DT,Pos.NN},
				new Pos[]{Pos.NN,Pos.IN,Pos.NN},
				new Pos[]{Pos.NN,Pos.VBZ},
				new Pos[]{Pos.JJ,Pos.NNS},
				new Pos[]{Pos.JJ,Pos.NN},
				new Pos[]{Pos.NN,Pos.NNS},
				new Pos[]{Pos.NN},
				new Pos[]{Pos.DT,Pos.NN},
				new Pos[]{Pos.IN,Pos.DT,Pos.NN},
			},
			new Pos[][] {
				new Pos[]{Pos.PRP$,Pos.NNS},
				new Pos[]{Pos.NNP},
				new Pos[]{Pos.NN,Pos.VBZ},
				new Pos[]{Pos.NN,Pos.IN,Pos.NN},
				new Pos[]{Pos.VBG,Pos.NNS},
				new Pos[]{Pos.VBG},
				new Pos[]{Pos.VBG,Pos.NN},
				new Pos[]{Pos.RB},
				new Pos[]{Pos.PRP$,Pos.NN},
				new Pos[]{Pos.IN,Pos.NNS},
				new Pos[]{Pos.DT,Pos.JJ,Pos.NN},
				new Pos[]{Pos.JJ},
				new Pos[]{Pos.IN,Pos.NN},
				new Pos[]{Pos.IN,Pos.DT,Pos.NN},
				new Pos[]{Pos.JJ,Pos.NNS},
				new Pos[]{Pos.NN,Pos.NNS},
				new Pos[]{Pos.JJ,Pos.NN},
				new Pos[]{Pos.DT,Pos.NN},
				new Pos[]{Pos.NNS},
				new Pos[]{Pos.NN},
			},
		};
		
//		final private static HaikuGrammarNode root1 = initializeHaikuGrammarTree(1);
//		final private static HaikuGrammarNode root2 = initializeHaikuGrammarTree(2);
//		final private static HaikuGrammarNode root3 = initializeHaikuGrammarTree(3);
		final private static HaikuGrammarNode root1 = initializeHaikuGrammarTreeFromData(1);
		final private static HaikuGrammarNode root2 = initializeHaikuGrammarTreeFromData(2);
		final private static HaikuGrammarNode root3 = initializeHaikuGrammarTreeFromData(3);
		final private static HaikuGrammarNode rootAll = initializeHaikuGrammarTreeFromData(4);
		
		private HaikuGrammarNode currentNode;
		
		public HaikuGrammarValidator(int line) {
			currentNode = line == 1?root1:(line == 2?root2:(line == 3?root3:rootAll));
		}

		public boolean validate(Pos pos) {
			if (pos.equals(currentNode.pos)) {
				return true;
			} else {
				currentNode = currentNode.getNextNodeTo(pos);
				if (currentNode == null) {
					return false;
				} else {
					return true;
				}
			}
		}
		
		public boolean isComplete() {
			return currentNode.pathIsComplete();
		}

		private static HaikuGrammarNode initializeHaikuGrammarTree(int line) {
			HaikuGrammarNode root = new HaikuGrammarNode(null);
			for (Pos[] posPath : trainingPosSet[line-1]) {
				root.addPath(posPath, 0);
			}
			
			return root;
		}
		
		private static HaikuGrammarNode initializeHaikuGrammarTreeFromData(int line) {
			HaikuGrammarNode root = new HaikuGrammarNode(null);
			try {
				for (Entry<List<Pos>, Integer> entry : HaikuMain.loadHaikuWord(line).entrySet()) {
					if (entry.getValue() > 1)
						root.addPath(entry.getKey().toArray(new Pos[0]), 0);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
			return root;
		}
	}
	
	@Override
	public String toString() {
		return "In the (order+1)-length sequence leading up to and including this token, a correct grammatical structure must be present";
	}
}
