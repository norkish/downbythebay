package markov;

import java.util.LinkedList;

public class PositionedVariableOrderState {

	private LinkedList<Integer> statePrefix;
	private int position;

	public PositionedVariableOrderState(int position, LinkedList<Integer> stateIndex) {
		this.position = position;
		this.statePrefix = stateIndex;
	}

	public int getPosition() {
		return this.position;
	}

	public LinkedList<Integer> getStatePrefix() {
		return this.statePrefix;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + position;
		result = prime * result + ((statePrefix == null) ? 0 : statePrefix.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PositionedVariableOrderState other = (PositionedVariableOrderState) obj;
		if (position != other.position)
			return false;
		if (statePrefix == null) {
			if (other.statePrefix != null)
				return false;
		} else if (!statePrefix.equals(other.statePrefix))
			return false;
		return true;
	}

	public String toString() {
		return "pos " + position + " statePrefix" + statePrefix;
	}
}
