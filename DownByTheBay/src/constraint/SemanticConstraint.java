package constraint;

import linguistic.syntactic.Pos;
import markov.Token;
import java.util.LinkedList;
import semantic.word2vec.*;

public class SemanticConstraint<T> implements TransitionalConstraint<T> {

	private static final double minSimilarity = 0.5;
	private final String theme;

	public SemanticConstraint(String theme) {
		this.theme = theme;
	}

	private static boolean isNounOrVerbOrAdjective(Pos pos) {
		if (pos == Pos.NN ||
				pos == Pos.NNS ||
				pos == Pos.NNP ||
				pos == Pos.NNPS ||
				pos == Pos.VB ||
				pos == Pos.VBG ||
				pos == Pos.VBD ||
				pos == Pos.VBN ||
				pos == Pos.VBP ||
				pos == Pos.VBZ ||
				pos == Pos.JJ ||
				pos == Pos.JJR ||
				pos == Pos.JJS ) return true;
		return false;
	}

	@Override
	public String toString() {
		return "constraintTheme: " + theme;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, LinkedList<Token> token) {
		return false;
	}
}
