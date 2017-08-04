package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import markov.Token;

public class StartOfWordConstraint<T> implements StateConstraint<T> {

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
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
