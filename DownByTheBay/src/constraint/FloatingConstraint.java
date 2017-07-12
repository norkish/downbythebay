package constraint;

import java.util.Iterator;
import java.util.LinkedList;

import data.SyllableToken;
import markov.Token;

public class FloatingConstraint<T> implements DynamicConstraint<SyllableToken> {

	private int lengthOfSegment;
	private StaticConstraint<T> staticConstraint;
	
	/**
	 * 
	 * @param lengthOfSegment if 0, then only the constrained position is considered, 
	 * if 1, then the token immediately previous is also considered, etc.
	 * @param partsOfSpeech, parts of speech that validate the constraint if one of the tokens
	 * in the segment is in this set
	 */
	public FloatingConstraint(int lengthOfSegment, StaticConstraint<T> staticConstraint) {
		this.lengthOfSegment = lengthOfSegment;
		this.staticConstraint = staticConstraint;
	}

	@Override
	public String toString() {
		return "Current or one of previous " + lengthOfSegment + " syllables must satisfy constraint: " + staticConstraint;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			if (staticConstraint.isSatisfiedBy(token)){
				return true;
			}
			
			Iterator<Token> descendingIterator = fromState.descendingIterator();
			for (int i = 0; i < lengthOfSegment && descendingIterator.hasNext(); i++) {
				if (staticConstraint.isSatisfiedBy((SyllableToken) descendingIterator.next())) {
					return true;
				}
			}
			
			return false;
		}
	}
}
