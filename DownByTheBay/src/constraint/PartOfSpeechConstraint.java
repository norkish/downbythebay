package constraint;

import linguistic.syntactic.Pos;
import markov.Token;

public class PartOfSpeechConstraint<T> implements Constraint<T> {

	private Pos constraintPos;
	
	public PartOfSpeechConstraint(Pos pos) {
		this.constraintPos = pos;
	}

	@Override
	public boolean isSatisfiedBy(Token states) {
		// TODO Auto-generated method stub
		return false;
	}

}
