package constraint;

import linguistic.syntactic.Pos;

public class PartsOfSpeechConstraint<T> implements Constraint<T> {

	private Pos[] constraintPosChoices;
	
	public PartsOfSpeechConstraint(Pos[] pos) {
		constraintPosChoices = pos;
	}

	@Override
	public boolean isSatisfiedBy(T states) {
		// TODO Auto-generated method stub
		return false;
	}

}
