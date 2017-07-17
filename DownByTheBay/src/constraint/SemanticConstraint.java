package constraint;

import markov.Token;
import java.util.LinkedList;
import semantic.word2vec.*;

public class SemanticConstraint<T> implements DynamicConstraint<T> {

	private final String theme;

	public SemanticConstraint(String theme) {
		this.theme = theme;
	}

	@Override
	public String toString() {
		return "constraintTheme: " + theme;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, Token token) {
		return false;
	}
}
