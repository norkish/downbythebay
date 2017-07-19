package constraint;

import java.util.LinkedList;
import java.util.List;

import data.SyllableToken;
import linguistic.phonetic.PhonemeEnum;
import markov.Token;

public class HasCodaConstraint<T> implements StateConstraint<T> {

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> state, int i) {
		Token token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			final List<PhonemeEnum> phonemes = ((SyllableToken) token).getPhonemes();
			return (phonemes.get(phonemes.size()-1).ordinal() > 14);
		}
	}

	@Override
	public String toString() {
		return "Syllable has coda";
	}
}
