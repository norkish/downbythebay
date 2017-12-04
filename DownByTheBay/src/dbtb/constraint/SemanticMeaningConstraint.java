package dbtb.constraint;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dbtb.data.SyllableToken;
import dbtb.markov.Token;
import dbtb.semantic.word2vec.BadW2vInputException;
import dbtb.semantic.word2vec.VectorMath;
import dbtb.semantic.word2vec.W2vInterface;

public class SemanticMeaningConstraint<T> implements StateConstraint<T>{

	private static final W2vInterface W2VINTERFACE = new W2vInterface("news-lyrics-bom3"); 
	private Map<String, double[]> themeWords = new ConcurrentHashMap<String, double[]>();
//	private Set<String> choices = new HashSet<>();
	private static final double THRESHOLD = .35;
	private static final Map<String,double[]> VECTOR_DICT = new ConcurrentHashMap<>();
	
	public SemanticMeaningConstraint(HashSet<String> themeWords) throws BadW2vInputException {
		for (String themeWord : themeWords) {
			final double[] vectorForString = getVectorForString(themeWord);
			if (vectorForString != null) this.themeWords.put(themeWord, vectorForString);
			else System.out.println("WARNING: \"" + themeWord + "\", which was used as a theme word for a SemanticMeaningConstraint, has no vector");
		}
	}

	@Override
	public String toString() {
		return "Semantic meaning must match all of " + themeWords.keySet();
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		}
		else {
			String string = ((SyllableToken) token).getStringRepresentation();
			double[] vector = getVectorForString(string);
			if (vector == null) return false;
			
			for (double[] themeVector : themeWords.values()) {
				if (VectorMath.cosineSimilarity(vector,themeVector) >= THRESHOLD)
					return true;
			}
			
			return false;
		}
	}

	private synchronized double[] getVectorForString(String string) {
		double[] vector = VECTOR_DICT.get(string);
		if (vector == null) {
			try {
				vector = W2VINTERFACE.getVector(string.toLowerCase());
			}
			catch (BadW2vInputException e) {
				return null;
			}
			VECTOR_DICT.putIfAbsent(string, vector);
		}
		return vector;
	}

}
