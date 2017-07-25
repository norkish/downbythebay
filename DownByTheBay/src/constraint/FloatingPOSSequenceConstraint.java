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
//		return isSatisfiedByGrammarTreeMethod(fromState, toState);
		return isSatisfiedByParseNounPhraseMethod(fromState, toState);
	}

	LexicalizedParser lp = LexicalizedParser.loadModel("nlpdata/englishFactored.ser.gz");
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
				// TODO: if adding a noun of any sort, also add second path through an adjective node
				nextNode = new DBTBGrammarNode(nextPos);
				nextNodesByPos.put(nextPos, nextNode);
			}
			nextNode.addPath(posPath, idx+1);
		}

		public boolean pathIsComplete() {
			return isCompletePath;
		}

	}
	
	public static class DBTBGrammarValidator {
		
		final private static Pos[][] trainingPosSet = new Pos[][]{ // KEEP THESE SORTED ALPHABETICALLY TO AVOID DUPLICATION
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN}, // the law drinking from a straw, a whale with a polka dot tail
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN,Pos.IN,Pos.JJ,Pos.NNS}, // a moose with a pair of new shoes
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN,Pos.IN,Pos.NNS}, // a moose with a pair of shoes
			new Pos[]{Pos.NN,Pos.IN,Pos.JJ,Pos.NNS}, // a moose with new shoes
			new Pos[]{Pos.NN,Pos.IN,Pos.NNS}, // a moose with shoes
			new Pos[]{Pos.NN,Pos.VBG,Pos.DT,Pos.JJ, Pos.NN}, // A frog eating a big dog
			new Pos[]{Pos.NN,Pos.VBG,Pos.DT,Pos.NN}, // A frog eating a dog
			new Pos[]{Pos.NN,Pos.VBG,Pos.NN,Pos.NNS}, // a llama wearing polka dot pajamas
			new Pos[]{Pos.NN,Pos.VBG,Pos.NNS}, // a llama wearing pajamas
			new Pos[]{Pos.NN,Pos.VBG,Pos.PRP$,Pos.NN}, // A bear combing his hair
			new Pos[]{Pos.NN,Pos.WDT,Pos.RB,Pos.VBD, Pos.DT, Pos.JJ,Pos.NN}, // a pirate that just ate a veggie diet
//			new Pos[]{},
		};
		
		final private static DBTBGrammarNode root = initializeDBTBGrammarTree();
		
		private DBTBGrammarNode currentNode = root;
		
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
