package dbtb.constraint;

import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.data.SyllableToken;
import dbtb.data.WordToken;
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
		Pos tokenPos = null;
		if (token instanceof StateToken) {
			if (((StateToken) token).token instanceof SyllableToken)
				tokenPos = ((StateToken<SyllableToken>) token).token.getPos();
			else if (((StateToken) token).token instanceof WordToken)
				tokenPos = ((StateToken<WordToken>) token).token.getPos();
			else 
				return false;
		} else if ((token instanceof SyllableToken)) {
			tokenPos = ((SyllableToken) token).getPos();
		} else if ((token instanceof WordToken)){
			tokenPos = ((WordToken) token).getPos();
		} else {
			return false;
		}
		return constraintPosChoices.contains(tokenPos);
	}

}
