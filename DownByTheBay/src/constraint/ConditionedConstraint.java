package constraint;


public class ConditionedConstraint<T> {
	public boolean getDesiredConditionState() {
		return desiredConditionState;
	}

	public Constraint<T> getConstraint() {
		return constraint;
	}

	// We allow for a constraint to enforce a condition or the negation of the condition
	private boolean desiredConditionState;
	private Constraint<T> constraint;

	public ConditionedConstraint(Constraint<T> constraint) {
		this.constraint = constraint;
		this.desiredConditionState = true;
	}

	public ConditionedConstraint(Constraint<T> constraint, boolean desiredConditionState) {
		this.constraint = constraint;
		this.desiredConditionState = desiredConditionState;
	}
	
	public String toString(){
		return (desiredConditionState?"":"¬") + constraint.toString();
	}
}
