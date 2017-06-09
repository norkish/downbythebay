package constraint;

import data.SyllableToken;
import markov.Token;

public class EndOfWordConstraint<T> implements StaticConstraint<SyllableToken> {

	@Override
	public boolean isSatisfiedBy(Token token) {
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
