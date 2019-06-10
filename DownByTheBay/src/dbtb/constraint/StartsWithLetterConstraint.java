package dbtb.constraint;

import java.util.LinkedList;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.data.SyllableToken;
import dbtb.data.WordToken;

public class StartsWithLetterConstraint<T> implements StateConstraint<T>  {
	private char letter;
	
	public StartsWithLetterConstraint(char letter) {
		this.letter = Character.toLowerCase(letter);
	}
	
	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
		char firstChar = 0;
		if (token instanceof StateToken) {
			if (((StateToken) token).token instanceof SyllableToken)
				firstChar = ((StateToken<SyllableToken>) token).token.getStringRepresentation().charAt(0);
			else if (((StateToken) token).token instanceof WordToken)
				firstChar = ((StateToken<WordToken>) token).token.getStringRepresentation().charAt(0);
			else 
				return false;
		} else if ((token instanceof SyllableToken)) {
			firstChar = ((SyllableToken) token).getStringRepresentation().charAt(0);
		} else if ((token instanceof WordToken)) {
			firstChar = ((WordToken) token).getStringRepresentation().charAt(0);
		} else {
			return false;
		}
		return Character.toLowerCase(firstChar) == letter;
	}

	@Override
	public String toString() {
		return "word must start with \"" + letter + "\"";
	}
}
