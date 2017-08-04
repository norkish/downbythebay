package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import markov.Token;

public class AbsoluteStressConstraint<T> implements StateConstraint<T> {

	@Override
	public String toString() {
		return "constraintStress:" + constraintStress;
	}

	private int constraintStress;
	
	public AbsoluteStressConstraint(int stress) {
		this.constraintStress = stress;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return (((SyllableToken) token).getStress() == 1) == (constraintStress == 1);
		}
	}

}
