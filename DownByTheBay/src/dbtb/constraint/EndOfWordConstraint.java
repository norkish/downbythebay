package dbtb.constraint;

import java.util.LinkedList;

import dbtb.data.SyllableToken;
import dbtb.markov.Token;

public class EndOfWordConstraint<T> implements StateConstraint<T> {

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
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
