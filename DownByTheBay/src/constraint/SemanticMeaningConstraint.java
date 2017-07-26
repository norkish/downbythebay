package constraint;

import java.util.*;

import data.SyllableToken;
import markov.Token;
import semantic.word2vec.BadW2vInputException;
import semantic.word2vec.VectorMath;
import semantic.word2vec.W2vInterface;
import semantic.word2vec.W2vOperations;

public class SemanticMeaningConstraint<T> implements StateConstraint<T>{

	String theme;
//	private Set<String> choices = new HashSet<>();
	private final double threshold = .5;
	public static Map<String,double[]> vectorDict = new HashMap<>();
	
	public SemanticMeaningConstraint(String theme) throws BadW2vInputException {
		this.theme = theme;
		vectorDict.putIfAbsent(theme, W2vInterface.getVector(theme));

//		if (theme.equals("nature")) {
//			choices.add("nature");
//			choices.add("tree");
//			choices.add("trees");
//			choices.add("shadow");
//			choices.add("sun");
//			choices.add("sunset");
//			choices.add("beach");
//			choices.add("sand");
//			choices.add("ocean");
//			choices.add("smell");
//			choices.add("breeze");
//			choices.add("light");
//			choices.add("air");
//			choices.add("grass");
//			choices.add("mountain");
//			choices.add("mountains");
//			choices.add("wind");
//			choices.add("water");
//			choices.add("earth");
//			choices.add("fire");
//		}
	}

	@Override
	public String toString() {
		return "Semantic meaning: " + theme;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> state, int i) {
		Token token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		}
		else {
			try {
				String string = ((SyllableToken) token).getStringRepresentation();
				double[] vector = W2vInterface.getVector(string);
				vectorDict.putIfAbsent(string,vector);
				if (VectorMath.cosineSimilarity(vector,vectorDict.get(theme)) >= threshold)
					return true;
				return false;
			}
			catch (BadW2vInputException e) {
				return false;
			}
		}
	}

}
