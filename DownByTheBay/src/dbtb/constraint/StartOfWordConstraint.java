package dbtb.constraint;

import java.util.LinkedList;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.data.SyllableToken;

public class StartOfWordConstraint<T> implements StateConstraint<T> {

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
		SyllableToken sToken;
		if (token instanceof StateToken) {
			sToken = ((StateToken<SyllableToken>) token).token;
		} else if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			sToken = (SyllableToken) token;
		}
		return sToken.getPositionInContext() == 0;
	}

	private final String string = "Must be first syllable in a word";
	@Override
	public String toString() {
		return string;
	}

}
