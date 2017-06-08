package constraint;

import data.SyllableToken;
import markov.Token;

public class StartOfWordConstraint<T> implements StaticConstraint<SyllableToken> {

	@Override
	public boolean isSatisfiedBy(Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return ((SyllableToken) token).getPositionInContext() == 0;
		}
	}

}
