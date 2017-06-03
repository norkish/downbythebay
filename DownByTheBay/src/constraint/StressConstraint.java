package constraint;

import data.SyllableToken;
import markov.Token;

public class StressConstraint<T> implements Constraint<T> {

	private int constraintStress;
	
	public StressConstraint(int stress) {
		this.constraintStress = stress;
	}

	@Override
	public boolean isSatisfiedBy(Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return ((SyllableToken) token).getStress() == constraintStress;
		}
	}

}
