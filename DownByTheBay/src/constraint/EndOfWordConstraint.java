package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import markov.Token;

public class EndOfWordConstraint<T> implements StateConstraint<SyllableToken> {

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> state, int i) {
		Token token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			final SyllableToken syllableToken = (SyllableToken) token;
			return syllableToken.getPositionInContext() == (syllableToken.getCountOfSylsInContext()-1);
		}
	}

	private final String string = "Must be last syllable in a word";
	@Override
	public String toString() {
		return string;
	}

}
