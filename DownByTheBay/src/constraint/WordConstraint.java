package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import markov.Token;

public class WordConstraint<T> implements StateConstraint<T> {

	private String word;
	private boolean mustMatchCase;
	
	public WordConstraint(String word, boolean mustMatchCase) {
		this.word = word;
		this.mustMatchCase = mustMatchCase;
	}
	
	@Override
	public boolean isSatisfiedBy(LinkedList<Token> state, int i) {
		Token token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			if (mustMatchCase)
				return ((SyllableToken) token).getStringRepresentation().equals(word);
			else
				return ((SyllableToken) token).getStringRepresentation().equalsIgnoreCase(word);
		}
	}

	@Override
	public String toString() {
		return "word " + (mustMatchCase?"and case ":"") +"must match: \"" + word + "\"";
	}
}
