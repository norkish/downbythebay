package constraint;

import markov.Token;

public interface StaticConstraint<T> extends Constraint<T>{

	abstract boolean isSatisfiedBy(Token state);

}
