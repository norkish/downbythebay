package dbtb.constraint;

import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.data.SyllableToken;
import dbtb.linguistic.syntactic.Pos;
import dbtb.markov.Token;

public class PartsOfSpeechConstraint<T> implements StateConstraint<T> {

	private Set<Pos> constraintPosChoices;
	
	public PartsOfSpeechConstraint(Set<Pos> constraintPosChoices) {
		this.constraintPosChoices = constraintPosChoices;
	}

	@Override
	public String toString() {
		return "POSes:" + StringUtils.join(constraintPosChoices);
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
		SyllableToken sToken;
		if (token instanceof StateToken) {
			sToken = ((StateToken<SyllableToken>) token).token;
		} else if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			sToken = ((SyllableToken) token);
		}
		Pos tokenPos = sToken.getPos();
		return constraintPosChoices.contains(tokenPos);
	}

}
