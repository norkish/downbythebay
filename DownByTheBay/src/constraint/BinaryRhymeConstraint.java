package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import linguistic.phonetic.PhonemeEnum;
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
	
	public boolean isSatisfiedBy(LinkedList<Token> fromState, Token token) {
		Token previousToken = fromState.get(fromState.size() - constraintSylsPrevToRhymeWith);
		
		if (!(previousToken instanceof SyllableToken) || !(token instanceof SyllableToken)) {
			return false;
		}
		
		SyllableToken syl1Token = (SyllableToken) previousToken;
		SyllableToken syl2Token = (SyllableToken) token;
		
		// TODO: BEN - implement function to determine if previousToken and token rhyme
		
		PhonemeEnum syl1VowelPhoneme = null;
		for (PhonemeEnum syl1Phoneme : syl1Token.getPhonemes()) {
			if (syl1Phoneme.isVowel()) {
				syl1VowelPhoneme = syl1Phoneme;
				break;
			}
		}
		PhonemeEnum syl2VowelPhoneme = null;
		for (PhonemeEnum syl2Phoneme : syl2Token.getPhonemes()) {
			if (syl2Phoneme.isVowel()) {
				syl2VowelPhoneme = syl2Phoneme;
				break;
			}
		}
		
		return syl1VowelPhoneme.compareTo(syl2VowelPhoneme) == 0 && syl1Token.getStress() == syl2Token.getStress();
	}

	@Override
	public String toString() {
		return "Rhyme with syllable " + constraintSylsPrevToRhymeWith + " previous";
	}

}
