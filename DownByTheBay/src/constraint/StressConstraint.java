package constraint;

import markov.Token;

public class StressConstraint<T> implements Constraint<T> {

	private int constraintStress;
	
	public StressConstraint(int stress) {
		this.constraintStress = stress;
	}

	@Override
	public boolean isSatisfiedBy(Token states) {
		// TODO Auto-generated method stub
		return false;
	}

}
