package constraint;

import linguistic.syntactic.Pos;
import markov.Token;

public class PartsOfSpeechConstraint<T> implements Constraint<T> {

	private Pos[] constraintPosChoices;
	
	public PartsOfSpeechConstraint(Pos[] pos) {
		constraintPosChoices = pos;
	}

	@Override
	public boolean isSatisfiedBy(Token states) {
		// TODO Auto-generated method stub
		return false;
	}

}
