package constraint;

import java.util.Arrays;

import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.Token;

public class PartsOfSpeechConstraint<T> implements Constraint<T> {

	private Pos[] constraintPosChoices;
	
	public PartsOfSpeechConstraint(Pos[] pos) {
		constraintPosChoices = pos;
	}

	@Override
	public String toString() {
		return "POSes:" + Arrays.toString(constraintPosChoices);
	}

	@Override
	public boolean isSatisfiedBy(Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			Pos tokenPos = ((SyllableToken) token).getPos();
			for (Pos pos : constraintPosChoices) {
				if (tokenPos.equals(pos)) {
					return true;
				}
			}
			return false;
		}
	}

}
