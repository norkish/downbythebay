package constraint;

import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.Token;

public class PartOfSpeechConstraint<T> implements Constraint<T> {

	@Override
	public String toString() {
		return "" + constraintPos;
	}

	private Pos constraintPos;
	
	public PartOfSpeechConstraint(Pos pos) {
		this.constraintPos = pos;
	}

	@Override
	public boolean isSatisfiedBy(Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return ((SyllableToken) token).getPos().equals(constraintPos);
		}
	}

}
