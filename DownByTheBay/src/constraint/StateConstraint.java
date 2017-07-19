package constraint;

import java.util.LinkedList;

import markov.Token;

public interface StateConstraint<T> extends Constraint<T>{

	abstract boolean isSatisfiedBy(LinkedList<Token> state, int i);

}
