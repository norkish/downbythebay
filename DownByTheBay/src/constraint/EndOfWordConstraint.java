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

}
