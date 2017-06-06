package constraint;

import java.util.LinkedList;

import markov.Token;

public interface DynamicConstraint<T> extends Constraint<T>{

	boolean isSatisfiedBy(LinkedList<Token> fromState, Token token);

}
