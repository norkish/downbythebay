package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import linguistic.syntactic.Pos;
import markov.Token;

public class PartOfSpeechConstraint<T extends Token> implements StateConstraint<T> {

	@Override
	public String toString() {
		return "POS:" + constraintPos;
	}

	private Pos constraintPos;
	
	public PartOfSpeechConstraint(Pos pos) {
		this.constraintPos = pos;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		Token token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return ((SyllableToken) token).getPos().equals(constraintPos);
		}
	}

}
