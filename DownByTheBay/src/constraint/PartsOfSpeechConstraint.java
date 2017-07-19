package constraint;

import java.util.LinkedList;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.Token;

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
	public boolean isSatisfiedBy(LinkedList<Token> state, int i) {
		Token token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			Pos tokenPos = ((SyllableToken) token).getPos();
			return constraintPosChoices.contains(tokenPos);
		}
	}

}
