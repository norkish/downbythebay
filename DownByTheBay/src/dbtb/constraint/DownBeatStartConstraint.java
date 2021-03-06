package dbtb.constraint;

import java.util.LinkedList;

import dbtb.data.RhythmToken;

public class DownBeatStartConstraint implements StateConstraint<RhythmToken> {

	@Override
	public boolean isSatisfiedBy(LinkedList<RhythmToken> state, int i) {
		return state.get(i).getOffsetFromDownbeat() == 0.0;
	}

}
