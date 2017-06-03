package constraint;

import data.SyllableToken;
import markov.Token;

public class BinaryRhymeConstraint<T> implements Constraint<T> {

	// if this value, for example, is 2, then the constraint indicates that the syllable 
	// under this constraint must rhyme with the syllable 2 positions before it
	// this must be greater than the Markov order and cannot point to a position before the 0th position 
	private int constraintSylsPrevToRhymeWith;
	
	public BinaryRhymeConstraint(int sylsPrevToRhymeWith) {
		constraintSylsPrevToRhymeWith = sylsPrevToRhymeWith;
	}

	@Override
	public boolean isSatisfiedBy(Token token) {
		throw new IllegalStateException("Cannot satisfy a binary rhyme constraint with single token");
	}
	
	public boolean isSatisfiedBy(Token[] fromState, Token token) {
		Token previousToken = fromState[fromState.length - constraintSylsPrevToRhymeWith];
		
		if (!(previousToken instanceof SyllableToken) || !(token instanceof SyllableToken)) {
			return false;
		}
		
		SyllableToken syl1Token = (SyllableToken) previousToken;
		SyllableToken syl2Token = (SyllableToken) token;
		
		// TODO: BEN - implement function to determine if previousToken and token rhyme
		
		return false;
	}

}
