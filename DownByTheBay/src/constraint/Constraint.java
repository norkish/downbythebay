package constraint;

public interface Constraint<T> {

	abstract boolean isSatisfiedBy(T states);

}
