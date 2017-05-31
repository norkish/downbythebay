package constraint;

import linguistic.syntactic.Pos;

public class PartOfSpeechConstraint<T> implements Constraint<T> {

	private Pos constraintPos;
	
	public PartOfSpeechConstraint(Pos pos) {
		this.constraintPos = pos;
	}

	@Override
	public boolean isSatisfiedBy(T states) {
		// TODO Auto-generated method stub
		return false;
	}

}
