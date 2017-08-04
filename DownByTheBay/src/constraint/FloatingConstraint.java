package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import markov.Token;

public class FloatingConstraint<T extends Token> implements TransitionalConstraint<T> {

	private int lengthOfSegment;
	private StateConstraint<T> staticConstraint;
	
	/**
	 * 
	 * @param lengthOfSegment if 0, then only the constrained position is considered, 
	 * if 1, then the token immediately previous is also considered, etc.
	 * @param partsOfSpeech, parts of speech that validate the constraint if one of the tokens
	 * in the segment is in this set
	 */
	public FloatingConstraint(int lengthOfSegment, StateConstraint<T> staticConstraint) {
		this.lengthOfSegment = lengthOfSegment;
		this.staticConstraint = staticConstraint;
	}

	@Override
	public String toString() {
		return "Current or one of previous " + lengthOfSegment + " syllables must satisfy constraint: " + staticConstraint;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<T> fromState, LinkedList<T> toState) {
		if (staticConstraint.isSatisfiedBy(toState,toState.size()-1)){
			return true;
		}
		
		final int furthestStateIdxBackToConsider = Math.max(0, fromState.size()-lengthOfSegment);
		for (int i = fromState.size()-1; i >= furthestStateIdxBackToConsider; i--) {
			if (staticConstraint.isSatisfiedBy(fromState, i)) {
				return true;
			}
		}
		
		return false;
	}
}
