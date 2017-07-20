package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import linguistic.phonetic.syllabic.Syllabifier;
import linguistic.phonetic.syllabic.Syllable;
import markov.Token;

public class RelativeStressConstraint<T> implements StateConstraint<T> {

	// if this value, for example, is 2, then the constraint indicates that the syllable 
	// under this constraint must rhyme with the syllable 2 positions before it
	// this must be greater than the Markov order and cannot point to a position before the 0th position 
	private int constraintSylsPrevToCompareWith;
	private int compareValue;
	
	public RelativeStressConstraint(int sylsPrevToCompareWith, int compareValue) {
		this.constraintSylsPrevToCompareWith = sylsPrevToCompareWith;
		this.compareValue = compareValue;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> state, int i) {
		if (i - constraintSylsPrevToCompareWith < 0) {
			throw new RuntimeException("Can't compare stress at position " + i + " in state with that " + constraintSylsPrevToCompareWith + " positions previous");
		}
		Token token = state.get(i);
		Token previousToken = state.get(i - constraintSylsPrevToCompareWith);
		
		if (!(previousToken instanceof SyllableToken) || !(token instanceof SyllableToken)) {
			return false;
		}
		
		SyllableToken syl1Token = (SyllableToken) previousToken;
		SyllableToken syl2Token = (SyllableToken) token;
		
		Syllable s1 = Syllabifier.tokenToSyllable(syl1Token);
		Syllable s2 = Syllabifier.tokenToSyllable(syl2Token);
		
		int stress1 = s1.getStress();
		int stress2 = s2.getStress();
		
		if (stress1 == 1) stress1 = 2;
		else if (stress1 == 2) stress1 = 1;
		if (stress2 == 1) stress2 = 2;
		else if (stress2 == 2) stress2 = 1;
		
		if (compareValue > 0)
			return stress2 > stress1;
		else 
			return stress2 < stress1;
	}

	@Override
	public String toString() {
		return "Stress must be " + (compareValue > 0?"greater":"less") + " than that " + constraintSylsPrevToCompareWith + " position" + (constraintSylsPrevToCompareWith == 1?"":"s") + " previous";
	}
}
