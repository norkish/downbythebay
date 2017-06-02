package constraint;

import markov.Token;

public interface Constraint<T> {

	abstract boolean isSatisfiedBy(Token states);

}
