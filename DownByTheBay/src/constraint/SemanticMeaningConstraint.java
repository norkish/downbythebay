package constraint;

import java.util.HashSet;
import java.util.Set;

import data.SyllableToken;
import markov.Token;

public class SemanticMeaningConstraint<T> implements StaticConstraint<T>{

	String theme;
	private Set<String> choices = new HashSet<String>();
	
	public SemanticMeaningConstraint(String theme) {
		this.theme = theme;
		if (theme.equals("nature")) {
			choices.add("nature");
			choices.add("tree");
			choices.add("trees");
			choices.add("shadow");
			choices.add("sun");
			choices.add("sunset");
			choices.add("beach");
			choices.add("sand");
			choices.add("ocean");
			choices.add("smell");
			choices.add("breeze");
			choices.add("light");
			choices.add("air");
			choices.add("grass");
			choices.add("mountain");
			choices.add("mountains");
			choices.add("wind");
			choices.add("water");
			choices.add("earth");
			choices.add("fire");
		}
	}

	@Override
	public String toString() {
		return "Semantic meaning: " + theme;
	}

	@Override
	public boolean isSatisfiedBy(Token token) {
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return choices.contains(((SyllableToken) token).getStringRepresentation().toLowerCase());
		}
	}

}
