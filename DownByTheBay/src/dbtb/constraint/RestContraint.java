package dbtb.constraint;

import java.util.LinkedList;

import dbtb.data.RhythmToken;

public class RestContraint implements StateConstraint<RhythmToken> {

	@Override
	public boolean isSatisfiedBy(LinkedList<RhythmToken> state, int i) {
		return state.get(i).isRest();
	}

}
