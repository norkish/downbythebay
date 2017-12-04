package dbtb.constraint;

import java.util.LinkedList;
import java.util.List;

import dbtb.data.SyllableToken;
import dbtb.linguistic.paul.HirjeeMatrix;
import dbtb.linguistic.phonetic.ConsonantPhoneme;
import dbtb.linguistic.phonetic.PhonemeEnum;
import dbtb.linguistic.phonetic.syllabic.Rhymer;
import dbtb.linguistic.phonetic.syllabic.Syllabifier;
import dbtb.linguistic.phonetic.syllabic.Syllable;
import dbtb.markov.Token;

public class BinaryRhymeConstraint<T> implements TransitionalConstraint<T> {

	// if this value, for example, is 2, then the constraint indicates that the syllable 
	// under this constraint must rhyme with the syllable 2 positions before it
	// this must be greater than the Markov order and cannot point to a position before the 0th position 
	private int constraintSylsPrevToRhymeWith;
	
	public BinaryRhymeConstraint(int sylsPrevToRhymeWith) {
		constraintSylsPrevToRhymeWith = sylsPrevToRhymeWith;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<T> fromState, LinkedList<T> toState) {
		T token = toState.getLast();
		T previousToken = fromState.get(fromState.size() - constraintSylsPrevToRhymeWith);
		
		if (!(previousToken instanceof SyllableToken) || !(token instanceof SyllableToken)) {
			return false;
		}
		
		SyllableToken syl1Token = (SyllableToken) previousToken;
		SyllableToken syl2Token = (SyllableToken) token;
		
//		final String syl1Word = syl1Token.getStringRepresentation();
//		final String syl2Word = syl2Token.getStringRepresentation();
//		
//		// can't rhyme year with year or years; 
//		if (syl1Token.getPositionInContext() == syl2Token.getPositionInContext()) {
//			switch (syl1Word.length() - syl2Word.length()) {
//			case 1:
//				if ((syl2Word + "s").equalsIgnoreCase(syl1Word)) return false;
//				break;
//			case 0:
//				if ((syl1Word.equalsIgnoreCase(syl2Word))) return false;
//				break;
//			case -1:
//				if ((syl1Word + "s").equalsIgnoreCase(syl2Word)) return false;
//				break;
//			default:
//				
//			}
//		}
		
		Syllable s1 = Syllabifier.tokenToSyllable(syl1Token);
		Syllable s2 = Syllabifier.tokenToSyllable(syl2Token);
//		double score = HirjeeMatrix.scoreSyllables(s1, s2);
//		double score2 = Rhymer.score2SyllablesByGaOptimizedWeights(s1, s2);
//		if (score > HirjeeMatrix.HIRJEE_RHYME_THRESHOLD || score2 >= BEN_RHYME_THRESHOLD) {
//			System.out.println("For " + s1 + " and " + s2 + " in " + syl1Word + " and " + syl2Word + ", Hirjee's score=" + score + " (>=" + HirjeeMatrix.HIRJEE_RHYME_THRESHOLD + "?)");
//			System.out.println("For " + s1 + " and " + s2 + " in " + syl1Word + " and " + syl2Word + ", Ben's score=" + score2 + " (>=" + BEN_RHYME_THRESHOLD + "?)");
//			return true;
//		}
		return (!s1.getOnset().equals(s2.getOnset()) && s1.getNucleus().equals(s2.getNucleus()) && s1.getCoda().equals(s2.getCoda()));
	}
	
	final double BEN_RHYME_THRESHOLD = .85;

	@Override
	public String toString() {
		return "Rhyme with syllable " + constraintSylsPrevToRhymeWith + " previous";
	}

}
