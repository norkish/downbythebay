package constraint;

import java.util.LinkedList;
import java.util.List;

import javax.management.RuntimeErrorException;

import data.RhythmToken;

public class MatchStressToBeatsConstraint implements TransitionalConstraint<RhythmToken>{

	private List<Integer> stresses;
	private int numberOfStressedSylsToMatch;

	public MatchStressToBeatsConstraint(List<Integer> stresses, double proportion) {
		this.stresses = stresses;
		
//		int totalPrimaryStressedSyllables = 0;
//		for (Integer stress : stresses) {
//			if (stress == 1) {
//				totalPrimaryStressedSyllables++;
//			}
//		}
//		
//		this.numberOfStressedSylsToMatch = (int) (proportion * totalPrimaryStressedSyllables);
		this.numberOfStressedSylsToMatch = (int) (proportion * stresses.size());
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<RhythmToken> fromState, LinkedList<RhythmToken> toState) {
		if (numberOfStressedSylsToMatch == 0) {
			return true;
		}
		if (stresses.size() > (fromState.size()+1)) {
			throw new RuntimeException("Can't match stresses beyond markov length");
		}
		
		int numberOfStressesMatchedToBeats = 0;
		for (int i = 1; i <= stresses.size(); i++) {
			final RhythmToken rhythmToken = (toState.size()-i >= 0 ? toState.get(toState.size()-i):fromState.getFirst());
			if (stresses.get(stresses.size() - i) == 1 == (rhythmToken.getOffsetFromDownbeat() % 1.0 < 0.01)) {
				numberOfStressesMatchedToBeats++;
				if (numberOfStressesMatchedToBeats >= numberOfStressedSylsToMatch)
					return true;
			}
		}
		
		return false;
	}

	@Override
	public String toString() {
		return "match " + numberOfStressedSylsToMatch + " stressed syllables to beats in " + stresses + " to previous " + stresses.size() + " positions (current included)";
	}
}
