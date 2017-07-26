package constraint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import data.SyllableToken;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import linguistic.syntactic.Pos;
import markov.Token;

public class FloatingPOSSequenceConstraint<T> implements TransitionalConstraint<T> {

	// if this value, for example, is 2, then the constraint indicates that the syllable 
	// under this constraint must rhyme with the syllable 2 positions before it
	// this must be greater than the Markov order and cannot point to a position before the 0th position 

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, LinkedList<Token> toState) {
		return isSatisfiedByGrammarTreeMethod(fromState, toState);
//		return isSatisfiedByParseNounPhraseMethod(fromState, toState);
	}

	LexicalizedParser lp;
	private boolean isSatisfiedByParseNounPhraseMethod(LinkedList<Token> fromState, LinkedList<Token> toState) {
		List<CoreLabel> rawWords = new ArrayList<CoreLabel>();
		String word;
		SyllableToken syllableToken;
		for (Token token : fromState) {
			syllableToken = (SyllableToken) token;
			word = syllableToken.getStringRepresentationIfFirstSyllable();
			if (!word.isEmpty()){
				CoreLabel l = new CoreLabel();
				l.setWord(word);
				rawWords.add(l);
			}
		}
		syllableToken = (SyllableToken) toState.getLast();
		word = syllableToken.getStringRepresentationIfFirstSyllable();
		if (!word.isEmpty()){
			CoreLabel l = new CoreLabel();
			l.setWord(word);
			rawWords.add(l);
		}
		
		Tree parse = lp.apply(rawWords);
		for (Tree subtree: parse)
	    {
			if(subtree.label().value().equals("NP")) {
				System.out.println(subtree);
				return true;
			}
	    }
		return false;
	}

	private boolean isSatisfiedByGrammarTreeMethod(LinkedList<Token> fromState, LinkedList<Token> toState) {
		SyllableToken lastToken = (SyllableToken) toState.getLast();
		
		DBTBGrammarValidator validator = new DBTBGrammarValidator();

		for (Token token : fromState) {
			if(!validator.validate(((SyllableToken) token).getPos()))
				return false;
		}
		
		if (!validator.validate(lastToken.getPos()))
			return false;
		
		return validator.isComplete();
	}

	public static class DBTBGrammarNode {

		public Pos pos = null;
		private Map<Pos, DBTBGrammarNode> nextNodesByPos = new EnumMap<Pos,DBTBGrammarNode>(Pos.class);
		private boolean isCompletePath = false;

		public DBTBGrammarNode(Pos pos2) {
			this.pos = pos2;
		}

		public DBTBGrammarNode getNextNodeTo(Pos pos2) {
			return nextNodesByPos.get(pos2);
		}

		public void addPath(Pos[] posPath, int idx) {
			if (idx == posPath.length) {
				this.isCompletePath = true;
				return;
			}
			Pos nextPos = posPath[idx];
			DBTBGrammarNode nextNode = nextNodesByPos.get(nextPos);
			if (nextNode == null) {
				nextNode = new DBTBGrammarNode(nextPos);
				nextNodesByPos.put(nextPos, nextNode);

				// while creating new noun node, also create new intermediate adj node
				if (nextPos.equals(Pos.NN) || nextPos.equals(Pos.NNS)) {
					DBTBGrammarNode adjNode = nextNodesByPos.get(Pos.JJ);
					if (adjNode == null) {
						adjNode = new DBTBGrammarNode(Pos.JJ);
						nextNodesByPos.put(Pos.JJ, adjNode);
					}
					if (adjNode.nextNodesByPos.containsKey(nextPos)) {
						new RuntimeException("In adding noun, and therefore intermediate adj, found that adj already existed with " + nextPos + " following state");
					}
					adjNode.nextNodesByPos.put(nextPos, nextNode);
				} else if (nextPos.equals(Pos.VB) || nextPos.equals(Pos.VBD) || nextPos.equals(Pos.VBG) || nextPos.equals(Pos.VBN) || nextPos.equals(Pos.VBP) || nextPos.equals(Pos.VBZ)) {
					DBTBGrammarNode advNode = nextNodesByPos.get(Pos.RB);
					if (advNode == null) {
						advNode = new DBTBGrammarNode(Pos.RB);
						nextNodesByPos.put(Pos.RB, advNode);
					}
					if (advNode.nextNodesByPos.containsKey(nextPos)) {
						new RuntimeException("In adding verb, and therefore intermediate adv, found that adv already existed with " + nextPos + " following state");
					}
					advNode.nextNodesByPos.put(nextPos, nextNode);
				}
			}
			
			nextNode.addPath(posPath, idx+1);
		}

		public boolean pathIsComplete() {
			return isCompletePath;
		}

	}
	
	public static class DBTBGrammarValidator {
		
//		final private static Pos[][] trainingPosSet = new Pos[][]{ // KEEP THESE SORTED ALPHABETICALLY TO AVOID DUPLICATION
//			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN}, // the law drinking from a straw, a whale with a polka dot tail
//			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN,Pos.IN,Pos.JJ,Pos.NNS}, // a moose with a pair of new shoes
//			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN,Pos.IN,Pos.NNS}, // a moose with a pair of shoes
//			new Pos[]{Pos.NN,Pos.IN,Pos.JJ,Pos.NNS}, // a moose with new shoes
//			new Pos[]{Pos.NN,Pos.IN,Pos.NNS}, // a moose with shoes
//			new Pos[]{Pos.NN,Pos.VBG,Pos.DT,Pos.JJ, Pos.NN}, // A frog eating a big dog
//			new Pos[]{Pos.NN,Pos.VBG,Pos.DT,Pos.NN}, // A frog eating a dog
//			new Pos[]{Pos.NN,Pos.VBG,Pos.NN,Pos.NNS}, // a llama wearing polka dot pajamas
//			new Pos[]{Pos.NN,Pos.VBG,Pos.NNS}, // a llama wearing pajamas
//			new Pos[]{Pos.NN,Pos.VBG,Pos.PRP$,Pos.NN}, // A bear combing his hair
//			new Pos[]{Pos.NN,Pos.WDT,Pos.RB,Pos.VBD, Pos.DT, Pos.JJ,Pos.NN}, // a pirate that just ate a veggie diet
////			new Pos[]{},
//		};
		final private static Pos[][] trainingPosSet = new Pos[][]{ // KEEP THESE SORTED ALPHABETICALLY TO AVOID DUPLICATION
			new Pos[]{Pos.NN,Pos.DT,Pos.NN,Pos.VBP}, // the way some people say
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN}, // the law drinking from a straw, a whale with a polka dot tail, a mouse out of the house
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN,Pos.IN,Pos.NN}, // a moose with a pair of new shoes
			new Pos[]{Pos.NN,Pos.IN,Pos.NN,Pos.CC,Pos.NNS}, // the pleas for help and screams
			new Pos[]{Pos.NN,Pos.IN,Pos.NNS}, // a moose with new shoes, a bear with hair
			new Pos[]{Pos.NN,Pos.JJ,Pos.TO,Pos.DT,Pos.NN}, // a wall next to a mall
			new Pos[]{Pos.NN,Pos.VBD,Pos.JJ,Pos.IN,Pos.NN}, // a tray piled high with hay
			new Pos[]{Pos.NN,Pos.VBG,Pos.DT, Pos.NN}, // A frog eating a big dog
			new Pos[]{Pos.NN,Pos.VBG,Pos.IN,Pos.NN}, // A cage dangling in space
			new Pos[]{Pos.NN,Pos.VBG,Pos.NN,Pos.NNS}, // a llama wearing polka dot pajamas
			new Pos[]{Pos.NN,Pos.VBG,Pos.NNS}, // a llama wearing pajamas
			new Pos[]{Pos.NN,Pos.VBG,Pos.PRP$,Pos.NN}, // A bear combing his hair
			new Pos[]{Pos.NN,Pos.VBG,Pos.RP,Pos.PRP$,Pos.NN}, // A tear trickling down his beard
			new Pos[]{Pos.NN,Pos.WDT,Pos.VBD, Pos.DT,Pos.NN}, // a pirate that just ate a veggie diet
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.JJ,Pos.NN}, // the words of the next verse
			new Pos[]{Pos.NN,Pos.PDT,Pos.DT,Pos.NNS,Pos.VBP}, // the way all the books say
//			a bird eating a worm
//			a bird eyeing a worm
//			a breeze blowing off the sea
//			a chance to go to France
		};
		
		final private static DBTBGrammarNode root = initializeDBTBGrammarTree();
		
		private DBTBGrammarNode currentNode = root;
		
		public boolean validate(Pos pos) {
			if (pos.equals(Pos.NNS)) {
				pos = Pos.NN;
			}

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

		private static DBTBGrammarNode initializeDBTBGrammarTree() {
			DBTBGrammarNode root = new DBTBGrammarNode(null);
			for (Pos[] posPath : trainingPosSet) {
				root.addPath(posPath, 0);
			}
			
			return root;
		}
	}
	
	@Override
	public String toString() {
		return "In the sequence spanning between the two rhyming words, a correct grammatical structure must be present";
	}
}