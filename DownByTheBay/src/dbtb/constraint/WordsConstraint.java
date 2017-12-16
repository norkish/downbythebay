package dbtb.constraint;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.data.SyllableToken;
import dbtb.markov.Token;

public class WordsConstraint<T> implements StateConstraint<T> {

	private Set<String> choices;
	private boolean mustMatchCase;
	
	public WordsConstraint(Set<String> choices, boolean mustMatchCase) {
		this.mustMatchCase = mustMatchCase;
		if (mustMatchCase) {
			this.choices = choices;
		} else {
			this.choices = new HashSet<String>();
			for (String choice : choices) {
				this.choices.add(choice.toLowerCase());
			}
		}
	}
	
	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
		SyllableToken sToken;
		if (token instanceof StateToken) {
			sToken = ((StateToken<SyllableToken>) token).token;
		}else if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			sToken = ((SyllableToken) token);
		}
		if (mustMatchCase)
			return choices.contains(sToken.getStringRepresentation());
		else
			return choices.contains(sToken.getStringRepresentation().toLowerCase());
	}

	@Override
	public String toString() {
		return "word " + (mustMatchCase?"and case ":"") +"must belong to: \"" + Arrays.toString(choices.toArray()) + "\"";
	}
}
