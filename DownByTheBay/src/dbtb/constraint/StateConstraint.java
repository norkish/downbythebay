package dbtb.constraint;

import java.util.LinkedList;

public interface StateConstraint<T> extends Constraint<T>{

	abstract boolean isSatisfiedBy(LinkedList<T> state, int i);

}
