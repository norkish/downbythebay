package constraint;

import java.util.LinkedList;

import data.RhythmToken;

public class TimeSignatureConstraint implements TransitionalConstraint<RhythmToken> {

	private String time;

	public TimeSignatureConstraint(int beatsPerMeasure, int noteTypePerBeat) {
		this.time = "" + beatsPerMeasure + "/" + noteTypePerBeat;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<RhythmToken> fromState, LinkedList<RhythmToken> token) {
		for (RhythmToken rhythmToken : fromState) {
			if (!rhythmToken.getTime().equals(time)) {
				return false;
			}
		}
		
		return token.getLast().getTime().equals(time);
	}

	@Override
	public String toString() {
		return "Time Signature Constraint: " + time;
	}
	

}
