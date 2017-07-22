package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import linguistic.paul.HirjeeMatrix;
import linguistic.phonetic.syllabic.Syllabifier;
import linguistic.phonetic.syllabic.Syllable;
import markov.Token;

public class BinaryRhymeConstraint<T> implements TransitionalConstraint<T> {

	// if this value, for example, is 2, then the constraint indicates that the syllable 
	// under this constraint must rhyme with the syllable 2 positions before it
	// this must be greater than the Markov order and cannot point to a position before the 0th position 
	private int constraintSylsPrevToRhymeWith;
	
	public BinaryRhymeConstraint(int sylsPrevToRhymeWith) {
		constraintSylsPrevToRhymeWith = sylsPrevToRhymeWith;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, LinkedList<Token> toState) {
		Token token = toState.getLast();
		Token previousToken = fromState.get(fromState.size() - constraintSylsPrevToRhymeWith);
		
		if (!(previousToken instanceof SyllableToken) || !(token instanceof SyllableToken)) {
			return false;
		}
		
		SyllableToken syl1Token = (SyllableToken) previousToken;
		SyllableToken syl2Token = (SyllableToken) token;
		
		if (syl1Token.getStringRepresentation().equals(syl2Token.getStringRepresentation()) && syl1Token.getPositionInContext() == syl2Token.getPositionInContext()) {
			return false;
		}
		
		Syllable s1 = Syllabifier.tokenToSyllable(syl1Token);
		Syllable s2 = Syllabifier.tokenToSyllable(syl2Token);
		double score = HirjeeMatrix.scoreSyllables(s1, s2);
//		if (score > HirjeeMatrix.HIRJEE_RHYME_THRESHOLD)
//			System.out.println("For " + s1 + " in " + syl1Token.getStringRepresentation() + " and " + s2 + " in " + syl2Token.getStringRepresentation() + ", score=" + score);
		return score > HirjeeMatrix.HIRJEE_RHYME_THRESHOLD;
//		return (s1.getNucleus().equals(s2.getNucleus()) && s1.getCoda().equals(s2.getCoda()));
//		double score = Rhymer.score2SyllablesByGaOptimizedWeights(s1, s2);
//		if (score >= .80) System.out.println("For " + s1 + " in " + syl1Token.getStringRepresentation() + " and " + s2 + " in " + syl2Token.getStringRepresentation() + ", score=" + score);
//		return (score >= .85);
	}

	@Override
	public String toString() {
		return "Rhyme with syllable " + constraintSylsPrevToRhymeWith + " previous";
	}

}
