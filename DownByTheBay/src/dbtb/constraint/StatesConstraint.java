package dbtb.constraint;

import java.util.LinkedList;
import java.util.Set;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.markov.Token;

public class StatesConstraint<T extends Token> implements StateConstraint<T> {
	
	private Set<Integer> states;

	public StatesConstraint(Set<Integer> states) {
		this.states = states;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean isSatisfiedBy(LinkedList<T> token, int i) {
		T tokenElement = token.get(i);
		if (!(tokenElement instanceof StateToken)) {
			return false;
		} else {
			Integer state = ((StateToken<T>) tokenElement).state;
			return states.contains(state);
		}
	}

}
