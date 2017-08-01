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

public class FloatingDBTBPOSSequenceConstraint<T> implements TransitionalConstraint<T> {

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
			final SyllableToken syllableToken = (SyllableToken) token;
			final String stringRepresentation = syllableToken.getStringRepresentation();
			if(stringRepresentation.equals("for") || stringRepresentation.equals("into") || !validator.validate(syllableToken.getPos()))
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
		
		final private static Pos[][] trainingPosSet = new Pos[][]{ // KEEP THESE SORTED ALPHABETICALLY TO AVOID DUPLICATION
			new Pos[]{Pos.NN,Pos.CC,Pos.NN}, //	a mother and baby brother, 2the wall and mangled dolls
			new Pos[]{Pos.NN,Pos.CC,Pos.DT, Pos.NN}, //	a mother and baby brother, a ball or a baby doll, 1a spring or some such thing
			new Pos[]{Pos.NN,Pos.CC,Pos.DT,Pos.NN,Pos.IN,Pos.NN}, // a face and a mane of snakes
			new Pos[]{Pos.NN,Pos.CC,Pos.DT,Pos.NN,Pos.IN,Pos.DT,Pos.NN}, // 1a mouse or a rat in the house
			new Pos[]{Pos.NN,Pos.DT,Pos.NN,Pos.CC,Pos.DT}, // 1a cot and a chamber pot
			new Pos[]{Pos.NN,Pos.DT,Pos.NN,Pos.VBP}, // the way some people say
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN}, // the law drinking from a straw, a whale with a polka dot tail, a mouse out of the house, 1a car in the parking lot, the words of the next verse, 2a crow with a sore throat, 1a drawer by the front door, 1a job in a health club
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN,Pos.IN,Pos.NN}, // a moose with a pair of new shoes
			new Pos[]{Pos.NN,Pos.IN,Pos.DT,Pos.NN,Pos.IN,Pos.PRP$,Pos.NN}, // 1a man with a hat in his hands
			new Pos[]{Pos.NN,Pos.IN,Pos.NN}, // a moose with new shoes, a bear with hair, 2a child playing with fire, 1a face of sharp distaste, 1a fort with uneven boards, 2a loaf of garlic toast, 1a piece of bloody beef
			new Pos[]{Pos.NN,Pos.IN,Pos.NN,Pos.CC,Pos.NN}, // the pleas for help and screams
			new Pos[]{Pos.NN,Pos.IN,Pos.NN,Pos.IN,Pos.NN}, // 2a gang of rats with hands
			new Pos[]{Pos.NN,Pos.IN,Pos.NN,Pos.TO,Pos.NN}, // 1a line from time to time
			new Pos[]{Pos.NN,Pos.IN,Pos.NNP}, // 1the top of Glacier Rock
			new Pos[]{Pos.NN,Pos.IN,Pos.NNP,Pos.CC,Pos.NN}, // 1a plane through sleet and rain
			new Pos[]{Pos.NN,Pos.IN,Pos.PRP$,Pos.NN}, // 1a part of our car
//			new Pos[]{Pos.NN,Pos.IN,Pos.PRP,Pos.TO,Pos.VB}, // 1a place for us to stay
			new Pos[]{Pos.NN,Pos.IN,Pos.VBG,Pos.NN}, // 1a pit of swirling mist, 1the vine at budding time, 2a wheel of spinning steel
			new Pos[]{Pos.NN,Pos.JJ,Pos.TO,Pos.DT,Pos.NN}, // a wall next to a mall
			new Pos[]{Pos.NN,Pos.PDT,Pos.DT,Pos.NNS,Pos.VBP}, // the way all the books say
			new Pos[]{Pos.NN,Pos.PRP,Pos.RP}, // 1the truck lift itself up
			new Pos[]{Pos.NN,Pos.PRP,Pos.VBD,Pos.VBG,Pos.TO,Pos.VB}, // 2the thing I was trying to bring
			new Pos[]{Pos.NN,Pos.TO,Pos.DT,Pos.WRB,Pos.CC,Pos.DT,Pos.WP}, // 2a clue to the why and the who
//			new Pos[]{Pos.NN,Pos.TO,Pos.VB,Pos.CC,Pos.VB}, // 1a place to sit and wait
			new Pos[]{Pos.NN,Pos.TO,Pos.VB,Pos.IN,Pos.DT,Pos.NN}, // 1a place to stay for a few days
			new Pos[]{Pos.NN,Pos.TO,Pos.VB,Pos.RB,Pos.RB}, // 2a place to get away
			new Pos[]{Pos.NN,Pos.TO,Pos.VB,Pos.TO,Pos.NNP}, // a chance to go to France
			new Pos[]{Pos.NN,Pos.VBD,Pos.JJ,Pos.IN,Pos.NN}, // a tray piled high with hay
			new Pos[]{Pos.NN,Pos.VBG,Pos.DT, Pos.NN}, // A frog eating a big dog, a bird eating a worm, a bird eyeing a worm
			new Pos[]{Pos.NN,Pos.VBG,Pos.IN,Pos.NN}, // A cage dangling in space
			new Pos[]{Pos.NN,Pos.VBG,Pos.NN,Pos.NN}, // a llama wearing polka dot pajamas
			new Pos[]{Pos.NN,Pos.VBG,Pos.NN}, // a llama wearing pajamas
			new Pos[]{Pos.NN,Pos.VBG,Pos.PRP$,Pos.NN}, // A bear combing his hair, 1a man losing his land
			new Pos[]{Pos.NN,Pos.VBG,Pos.RP,Pos.PRP$,Pos.NN}, // A tear trickling down his beard
			new Pos[]{Pos.NN,Pos.VBG,Pos.RP,Pos.DT,Pos.NN}, // a breeze blowing off the sea
			new Pos[]{Pos.NN,Pos.VBN,Pos.IN,Pos.DT,Pos.NN},//1a cart pulled by an ox, 1a mouth set in a frown, 2a scar shaped like a star
			new Pos[]{Pos.NN,Pos.WDT,Pos.VBD,Pos.DT,Pos.NN}, // a pirate that just ate a veggie diet, 1a look that made the book
			new Pos[]{Pos.NN,Pos.WDT,Pos.VBP,Pos.IN,Pos.DT,Pos.NN}, // 2a stream that wound like a dream
			new Pos[]{Pos.NN,Pos.WDT,Pos.MD,Pos.VB,Pos.PRP,Pos.IN}, // the truths that will see us through
			new Pos[]{Pos.NN,Pos.WP,Pos.VBD,Pos.CC,Pos.VBD}, // 1a man who trained and ran
			new Pos[]{Pos.NN,Pos.WRB,Pos.DT,Pos.NN,Pos.NN,Pos.VBD}, // 2a land where no bridges spanned
			new Pos[]{Pos.NN,Pos.WRB,Pos.PRP,Pos.MD,Pos.VB}, // 1a place where I could stay
		};
		
		final private static DBTBGrammarNode root = initializeDBTBGrammarTree();
		
		private DBTBGrammarNode currentNode = root;
		
		public boolean validate(Pos pos) {
			if (pos.equals(Pos.NNS)) {
				pos = Pos.NN;
			} else if (pos.equals(Pos.NNPS)) {
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
