package constraint;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.Token;

public class PartOfSpeechInSegmentConstraint<T> implements DynamicConstraint<SyllableToken> {

	private int lengthOfSegment;
	private Set<Pos> partsOfSpeech;
	
	/**
	 * 
	 * @param lengthOfSegment if 0, then only the constrained position is considered, 
	 * if 1, then the token immediately previous is also considered, etc.
	 * @param partsOfSpeech, parts of speech that validate the constraint if one of the tokens
	 * in the segment is in this set
	 */
	public PartOfSpeechInSegmentConstraint(int lengthOfSegment, Set<Pos> partsOfSpeech) {
		this.lengthOfSegment = lengthOfSegment;
		this.partsOfSpeech = partsOfSpeech;
	}

	@Override
	public String toString() {
		return "Current or one of previous " + lengthOfSegment + " syllables must be in " + StringUtils.join(partsOfSpeech);
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			if (partsOfSpeech.contains(((SyllableToken) token).getPos())) {
				return true;
			}
			
			Iterator<Token> descendingIterator = fromState.descendingIterator();
			for (int i = 0; i < lengthOfSegment && descendingIterator.hasNext(); i++) {
				if (partsOfSpeech.contains(((SyllableToken) descendingIterator.next()).getPos())) {
					return true;
				}
			}
			
			return false;
		}
	}
}
