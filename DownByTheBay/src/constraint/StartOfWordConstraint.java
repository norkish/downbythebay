package constraint;

import data.SyllableToken;
import markov.Token;

public class StartOfWordConstraint<T> implements StaticConstraint<T> {

	@Override
	public boolean isSatisfiedBy(Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return ((SyllableToken) token).getPositionInContext() == 0;
		}
	}

	private final String string = "Must be first syllable in a word";
	@Override
	public String toString() {
		return string;
	}

}
