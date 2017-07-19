package constraint;

import java.util.LinkedList;

import markov.Token;

public interface TransitionalConstraint<T> extends Constraint<T>{

	boolean isSatisfiedBy(LinkedList<Token> fromState, LinkedList<Token> token);

}
