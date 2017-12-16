package dbtb.constraint;

import java.util.LinkedList;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.data.SyllableToken;

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
		SyllableToken sToken;
		
		if (token instanceof StateToken) {
			sToken = ((StateToken<SyllableToken>) token).token;
		} else if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			sToken = (SyllableToken) token;
		}
		return (sToken.getStress() == 1) == (constraintStress == 1);
	}

}
